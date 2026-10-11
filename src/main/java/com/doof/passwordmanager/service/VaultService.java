package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.Kdf;
import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.crypto.KeyHierarchy;
import com.doof.passwordmanager.crypto.SecureWipe;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.model.VaultMetadata;

import java.security.MessageDigest;
import java.time.Clock;
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
    private final KdfParams newVaultParams;
    private final Clock clock;
    private final Object unlockLock = new Object();
    private final Object keyLock = new Object();
    private byte[] encKey;                // guarded by keyLock
    private VaultMetadata cachedMetadata; // guarded by keyLock
    private final long sessionTimeoutMs = 5 * 60 * 1000L;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "doof-autolock");
        t.setDaemon(true);  // must not keep the JVM alive after the app exits
        return t;
    });
    private ScheduledFuture<?> autoLockTask;
    private final List<Runnable> lockListeners = new CopyOnWriteArrayList<>();

    VaultService(VaultMetadataDao metadataDao) {
        this(metadataDao, KdfParams.DEFAULT);
    }

    /** {@code newVaultParams} is used only when creating a vault; unlock reads params from kdf_params. */
    VaultService(VaultMetadataDao metadataDao, KdfParams newVaultParams) {
        this(metadataDao, newVaultParams, Clock.systemUTC());
    }

    VaultService(VaultMetadataDao metadataDao, KdfParams newVaultParams, Clock clock) {
        this.metadataDao = metadataDao;
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
        byte[] authKey = null;
        byte[] newEncKey = null;
        try {
            VaultMetadata meta = metadataDao.find();
            if (meta == null) {
                throw new RuntimeException("Vault is not initialized");
            }
            UnlockStatus status = statusOf(meta, clock.millis());
            if (status.isThrottled()) {
                throw new UnlockThrottledException(status);
            }
            if (meta.getEncVersion() != ENC_VERSION) {
                throw new RuntimeException("Unsupported vault encryption version " + meta.getEncVersion());
            }
            KdfParams params = KdfParams.fromJson(meta.getKdfParams());

            masterKey = Kdf.deriveMasterKey(masterPassword, meta.getKdfSalt(), params);
            authKey = KeyHierarchy.authKey(masterKey);
            if (!MessageDigest.isEqual(authKey, meta.getAuthHash())) {  // constant-time
                // Persist before reporting, so killing the app after a guess can't skip the count.
                int failed = metadataDao.recordFailedUnlock(clock.millis());
                throw new InvalidPasswordException(new UnlockStatus(failed, delayAfter(failed)));
            }
            if (meta.getFailedAttempts() > 0) {
                metadataDao.resetFailedUnlocks();
                meta.setFailedAttempts(0);
                meta.setLastFailedAt(null);
            }
            newEncKey = KeyHierarchy.encKey(masterKey);

            setUnlocked(newEncKey, meta);
            newEncKey = null;
        } finally {
            SecureWipe.wipe(masterPassword);
            SecureWipe.wipe(masterKey);
            SecureWipe.wipe(authKey);
            SecureWipe.wipe(newEncKey);
        }
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

    void rotateVault(char[] oldMasterPassword, char[] newMasterPassword, String newKdfParamsJson) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void setUnlocked(byte[] key, VaultMetadata metadata) {
        synchronized (keyLock) {
            SecureWipe.wipe(encKey);
            encKey = key;
            cachedMetadata = metadata;
        }
        startAutoLockTimer();
    }

    private synchronized void startAutoLockTimer() {
        cancelAutoLockTimer();
        autoLockTask = scheduler.schedule(this::lockVault, sessionTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelAutoLockTimer() {
        if (autoLockTask != null && !autoLockTask.isDone()) {
            autoLockTask.cancel(false);
        }
        autoLockTask = null;
    }

    void resetSessionTimer() {
        if (isUnlocked()) {
            startAutoLockTimer();
        }
    }
}
