package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.Kdf;
import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.crypto.KeyHierarchy;
import com.doof.passwordmanager.crypto.SecureWipe;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.model.VaultMetadata;

import java.security.MessageDigest;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Vault lifecycle: create, unlock, lock. Master password → Argon2id master key → HKDF
 * auth key (stored, compared on unlock) and enc key (memory only, wiped on lock).
 */
class VaultService {

    /** Format of vault crypto: Argon2id + HKDF split + AES-256-GCM with entry id as AAD. */
    static final int ENC_VERSION = 1;

    private final VaultMetadataDao metadataDao;
    private final KdfParams newVaultParams;
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

    VaultService(VaultMetadataDao metadataDao) {
        this(metadataDao, KdfParams.DEFAULT);
    }

    /** {@code newVaultParams} is used only when creating a vault; unlock reads params from kdf_params. */
    VaultService(VaultMetadataDao metadataDao, KdfParams newVaultParams) {
        this.metadataDao = metadataDao;
        this.newVaultParams = newVaultParams;
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

    /** Unlocks with the KDF params stored in the vault. Wipes {@code masterPassword}. */
    void unlockVault(char[] masterPassword) {
        byte[] masterKey = null;
        byte[] authKey = null;
        byte[] newEncKey = null;
        try {
            VaultMetadata meta = metadataDao.find();
            if (meta == null) {
                throw new RuntimeException("Vault is not initialized");
            }
            if (meta.getEncVersion() != ENC_VERSION) {
                throw new RuntimeException("Unsupported vault encryption version " + meta.getEncVersion());
            }
            KdfParams params = KdfParams.fromJson(meta.getKdfParams());

            masterKey = Kdf.deriveMasterKey(masterPassword, meta.getKdfSalt(), params);
            authKey = KeyHierarchy.authKey(masterKey);
            if (!MessageDigest.isEqual(authKey, meta.getAuthHash())) {  // constant-time
                throw new RuntimeException("Invalid master password");
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
