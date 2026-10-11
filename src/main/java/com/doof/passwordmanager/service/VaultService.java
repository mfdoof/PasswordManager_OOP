package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.AesGcm;
import com.doof.passwordmanager.crypto.Kdf;
import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.crypto.KeyHierarchy;
import com.doof.passwordmanager.crypto.SecureWipe;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.dao.VaultRotationDao;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.model.VaultMetadata;
import com.doof.passwordmanager.util.PasswordPolicy;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Vault lifecycle: create, unlock, lock. Master password → Argon2id master key → HKDF
 * auth key (stored, compared on unlock) and enc key (memory only, wiped on lock).
 * <p>
 * Unlock throttling (ARCHITECTURE §8): after the n-th consecutive failure, the next attempt
 * must wait min(1s * 2^(n-1), 30s). The counter lives in vault_metadata, so restarting the
 * app does not reset it; attempts during the wait are refused before the KDF runs.
 */
class VaultService {

    /** Format of vault crypto: Argon2id + HKDF split + AES-256-GCM with entry id as AAD. */
    static final int ENC_VERSION = 1;

    static final long BASE_DELAY_MS = 1_000;
    static final long MAX_DELAY_MS = 30_000;

    private final VaultMetadataDao metadataDao;
    private final VaultRotationDao rotationDao;
    private final KdfParams newVaultParams;
    private final Clock clock;
    private final Object unlockLock = new Object();
    private final Object keyLock = new Object();
    private byte[] encKey;                // guarded by keyLock
    private VaultMetadata cachedMetadata; // guarded by keyLock
    /** Idle time before auto-lock (ARCHITECTURE §8). */
    static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L;
    private volatile long lastActivityAt;  // clock millis of unlock or last activity
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "doof-autolock");
        t.setDaemon(true);  // must not keep the JVM alive after the app exits
        return t;
    });
    private ScheduledFuture<?> autoLockTask;
    private final List<Runnable> lockListeners = new CopyOnWriteArrayList<>();

    VaultService(VaultMetadataDao metadataDao, VaultRotationDao rotationDao) {
        this(metadataDao, rotationDao, KdfParams.DEFAULT, Clock.systemUTC());
    }

    /**
     * {@code newVaultParams} is used when creating a vault and when rotating (so rotation upgrades
     * old params); unlock always reads params from kdf_params.
     */
    VaultService(VaultMetadataDao metadataDao, VaultRotationDao rotationDao, KdfParams newVaultParams, Clock clock) {
        this.metadataDao = metadataDao;
        this.rotationDao = rotationDao;
        this.newVaultParams = newVaultParams;
        this.clock = clock;
    }

    /** Delay required after {@code failedAttempts} consecutive failures: 0, 1s, 2s, 4s, ... capped at 30s. */
    static long delayAfter(int failedAttempts) {
        if (failedAttempts <= 0) return 0;
        int shift = Math.min(failedAttempts - 1, 30);
        return Math.min(MAX_DELAY_MS, BASE_DELAY_MS << shift);
    }

    /** Current throttling state, e.g. for the unlock screen on startup. */
    UnlockStatus unlockStatus() {
        VaultMetadata meta = metadataDao.find();
        return meta == null ? new UnlockStatus(0, 0) : statusOf(meta, clock.millis());
    }

    private static UnlockStatus statusOf(VaultMetadata meta, long now) {
        int failed = meta.getFailedAttempts();
        Long lastFailedAt = meta.getLastFailedAt();
        if (failed <= 0 || lastFailedAt == null) {
            return new UnlockStatus(Math.max(failed, 0), 0);
        }
        long delay = delayAfter(failed);
        long elapsed = now - lastFailedAt;
        // Clock moved backwards: require the full delay rather than a huge or negative one.
        long remaining = elapsed < 0 ? delay : Math.max(0, delay - elapsed);
        return new UnlockStatus(failed, remaining);
    }

    boolean isVaultInitialized() {
        VaultMetadata meta = metadataDao.find();
        return meta != null;
    }

    /** Creates the vault and leaves it unlocked. Wipes {@code masterPassword}. */
    void createVault(char[] masterPassword) {
        byte[] masterKey = null;
        byte[] authKey = null;
        byte[] newEncKey = null;
        try {
            PasswordPolicy.validateMaster(masterPassword);
            if (isVaultInitialized()) {
                throw new RuntimeException("Vault already exists");
            }
            byte[] salt = Kdf.newSalt();
            masterKey = Kdf.deriveMasterKey(masterPassword, salt, newVaultParams);
            authKey = KeyHierarchy.authKey(masterKey);
            newEncKey = KeyHierarchy.encKey(masterKey);

            // Salt and auth key are stored on disk and stay referenced by the cached metadata,
            // so they are not wiped here. Only the master key and enc key are secret.
            VaultMetadata metadata = new VaultMetadata(salt, newVaultParams.toJson(), authKey, ENC_VERSION);
            metadataDao.insert(metadata);
            authKey = null;

            setUnlocked(newEncKey, metadata);
            newEncKey = null;
        } finally {
            SecureWipe.wipe(masterPassword);
            SecureWipe.wipe(masterKey);
            SecureWipe.wipe(newEncKey);
        }
    }

    /**
     * Unlocks with the KDF params stored in the vault. Wipes {@code masterPassword}.
     *
     * @throws UnlockThrottledException if still inside the delay after a failed attempt
     * @throws InvalidPasswordException if the password is wrong (the failure is recorded first)
     */
    void unlockVault(char[] masterPassword) {
        synchronized (unlockLock) {
            unlockSerialized(masterPassword);
        }
    }

    private void unlockSerialized(char[] masterPassword) {
        byte[] masterKey = null;
        byte[] newEncKey = null;
        try {
            VaultMetadata meta = requireMetadata();
            masterKey = verifyMasterPassword(meta, masterPassword);
            newEncKey = KeyHierarchy.encKey(masterKey);

            setUnlocked(newEncKey, meta);
            newEncKey = null;
        } finally {
            SecureWipe.wipe(masterPassword);
            SecureWipe.wipe(masterKey);
            SecureWipe.wipe(newEncKey);
        }
    }

    /**
     * Changes the master password (ARCHITECTURE §8). Verifies {@code oldPassword} with the same
     * throttling as unlock, then in ONE transaction re-encrypts every entry (same ids, so same AAD)
     * under keys from {@code newPassword} with a new salt and the current default KDF params, and
     * replaces the metadata. Any failure rolls back, so the vault still opens with the old password.
     * On success the session is unlocked with the new key. Wipes both password arrays.
     *
     * @throws UnlockThrottledException if still inside the delay after a failed attempt
     * @throws InvalidPasswordException if {@code oldPassword} is wrong (the failure is recorded first)
     */
    void rotateVault(char[] oldPassword, char[] newPassword) {
        synchronized (unlockLock) {
            byte[] oldMaster = null;
            byte[] oldEncKey = null;
            byte[] newMaster = null;
            byte[] newEncKey = null;
            try {
                PasswordPolicy.validateMaster(newPassword);
                VaultMetadata meta = requireMetadata();
                oldMaster = verifyMasterPassword(meta, oldPassword);
                oldEncKey = KeyHierarchy.encKey(oldMaster);

                byte[] newSalt = Kdf.newSalt();
                newMaster = Kdf.deriveMasterKey(newPassword, newSalt, newVaultParams);
                newEncKey = KeyHierarchy.encKey(newMaster);
                VaultMetadata newMeta = new VaultMetadata(
                        newSalt, newVaultParams.toJson(), KeyHierarchy.authKey(newMaster), ENC_VERSION);

                byte[] fromKey = oldEncKey, toKey = newEncKey;
                rotationDao.rotate(entries -> new VaultRotationDao.Rotation(newMeta, reencryptAll(entries, fromKey, toKey)));

                setUnlocked(newEncKey, newMeta);
                newEncKey = null;
            } finally {
                SecureWipe.wipe(oldPassword);
                SecureWipe.wipe(newPassword);
                SecureWipe.wipe(oldMaster);
                SecureWipe.wipe(oldEncKey);
                SecureWipe.wipe(newMaster);
                SecureWipe.wipe(newEncKey);
            }
        }
    }

    /** Decrypts each blob with {@code fromKey} and re-encrypts it with {@code toKey}, keeping id and AAD. */
    private static List<Entry> reencryptAll(List<Entry> entries, byte[] fromKey, byte[] toKey) {
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            byte[] aad = EntryService.aad(e.getId());
            byte[] plaintext = AesGcm.decrypt(fromKey, e.getIv(), e.getDataEnc(), aad);
            try {
                AesGcm.Sealed sealed = AesGcm.encrypt(toKey, plaintext, aad);
                Entry r = new Entry(sealed.ciphertext, sealed.iv, ENC_VERSION);
                r.setId(e.getId());
                out.add(r);
            } finally {
                SecureWipe.wipe(plaintext);
            }
        }
        return out;
    }

    private VaultMetadata requireMetadata() {
        VaultMetadata meta = metadataDao.find();
        if (meta == null) {
            throw new RuntimeException("Vault is not initialized");
        }
        if (meta.getEncVersion() != ENC_VERSION) {
            throw new RuntimeException("Unsupported vault encryption version " + meta.getEncVersion());
        }
        return meta;
    }

    /**
     * Throttle check, then Argon2id + constant-time auth key compare. A mismatch is recorded
     * before throwing; a match resets the failure counter. Returns the master key (caller wipes).
     */
    private byte[] verifyMasterPassword(VaultMetadata meta, char[] password) {
        UnlockStatus status = statusOf(meta, clock.millis());
        if (status.isThrottled()) {
            throw new UnlockThrottledException(status);
        }
        KdfParams params = KdfParams.fromJson(meta.getKdfParams());

        byte[] masterKey = Kdf.deriveMasterKey(password, meta.getKdfSalt(), params);
        byte[] authKey = KeyHierarchy.authKey(masterKey);
        try {
            if (!MessageDigest.isEqual(authKey, meta.getAuthHash())) {  // constant-time
                SecureWipe.wipe(masterKey);
                // Persist before reporting, so killing the app after a guess can't skip the count.
                int failed = metadataDao.recordFailedUnlock(clock.millis());
                throw new InvalidPasswordException(new UnlockStatus(failed, delayAfter(failed)));
            }
        } finally {
            SecureWipe.wipe(authKey);
        }
        if (meta.getFailedAttempts() > 0) {
            metadataDao.resetFailedUnlocks();
            meta.setFailedAttempts(0);
            meta.setLastFailedAt(null);
        }
        return masterKey;
    }

    void lockVault() {
        synchronized (keyLock) {
            SecureWipe.wipe(encKey);
            encKey = null;
            cachedMetadata = null;
        }
        cancelAutoLockTimer();
        // Outside keyLock so listeners may take their own locks without lock-order issues.
        for (Runnable listener : lockListeners) {
            listener.run();
        }
    }

    /** Runs {@code listener} after every lock, including auto-lock (on the timer thread). */
    void addLockListener(Runnable listener) {
        lockListeners.add(listener);
    }

    boolean isUnlocked() {
        synchronized (keyLock) {
            return encKey != null;
        }
    }

    VaultMetadata getMetadata() {
        synchronized (keyLock) {
            return cachedMetadata;
        }
    }

    /**
     * Runs {@code op} with the enc key. Holding the lock means auto-lock cannot wipe the key
     * mid-operation. {@code op} must not keep a reference to the key.
     */
    <T> T withEncKey(Function<byte[], T> op) {
        synchronized (keyLock) {
            if (encKey == null) {
                throw new RuntimeException("Vault is locked. Unlock first.");
            }
            return op.apply(encKey);
        }
    }

    private void setUnlocked(byte[] key, VaultMetadata metadata) {
        synchronized (keyLock) {
            SecureWipe.wipe(encKey);
            encKey = key;
            cachedMetadata = metadata;
        }
        lastActivityAt = clock.millis();
        startAutoLockTimer();
    }

    private synchronized void startAutoLockTimer() {
        cancelAutoLockTimer();
        autoLockTask = scheduler.schedule(this::lockVault, SESSION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelAutoLockTimer() {
        if (autoLockTask != null && !autoLockTask.isDone()) {
            autoLockTask.cancel(false);
        }
        autoLockTask = null;
    }

    /** Records activity: restarts the idle timeout. No effect while locked. */
    void resetSessionTimer() {
        if (isUnlocked()) {
            lastActivityAt = clock.millis();
            startAutoLockTimer();
        }
    }

    /**
     * Idle time left before auto-lock, by the injectable clock (0 when locked or expired). The UI
     * uses this for its countdown and locks at 0; the scheduled task above is a real-time safety net.
     */
    long sessionRemainingMs() {
        if (!isUnlocked()) return 0;
        return Math.max(0, lastActivityAt + SESSION_TIMEOUT_MS - clock.millis());
    }
}
