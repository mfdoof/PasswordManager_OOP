package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.model.VaultMetadata;
import com.doof.passwordmanager.util.PasswordHasher;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class VaultService {

    private final VaultMetadataDao metadataDao;
    private volatile SecretKey sessionKey;
    private VaultMetadata cachedMetadata;
    private final long sessionTimeoutMs = 5 * 60 * 1000L;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> autoLockTask;

    VaultService(VaultMetadataDao metadataDao) {
        this.metadataDao = metadataDao;
    }

    boolean isVaultInitialized() {
        VaultMetadata meta = metadataDao.find();
        return meta != null;
    }

    void createVault(char[] masterPassword) {
        if (isVaultInitialized()) {
            throw new RuntimeException("Vault already exists");
        }

        byte[] salt = null;
        byte[] masterHash = null;
        try {
            salt = PasswordHasher.generateSalt();
            masterHash = PasswordHasher.hashPassword(masterPassword, salt);
            String kdfParamsJson = PasswordHasher.getDefaultKdfParamsJson();

            VaultMetadata metadata = new VaultMetadata();
            metadata.setKdfSalt(salt);
            metadata.setKdfParams(kdfParamsJson);
            metadata.setMasterHash(masterHash);
            metadata.setEncVersion(1);

            metadataDao.insert(metadata);

            SecretKey key = PasswordHasher.deriveKey(masterPassword, salt);
            this.sessionKey = key;
            this.cachedMetadata = metadata;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create vault", e);
        } finally {
            PasswordHasher.wipe(masterPassword);
            if (masterHash != null) Arrays.fill(masterHash, (byte) 0);
            if (salt != null) Arrays.fill(salt, (byte) 0);
        }
    }

    void unlockVault(char[] masterPassword) {
        VaultMetadata meta = metadataDao.find();
        if (meta == null) {
            throw new RuntimeException("Vault is not initialized");
        }

        boolean verified;
        try {
            verified = PasswordHasher.verifyPassword(masterPassword, meta.getKdfSalt(), meta.getMasterHash());
            if (!verified) {
                throw new RuntimeException("Invalid master password");
            }

            SecretKey key = PasswordHasher.deriveKey(masterPassword, meta.getKdfSalt());
            this.sessionKey = key;
            this.cachedMetadata = meta;

            startAutoLockTimer();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to unlock vault", e);
        } finally {
            PasswordHasher.wipe(masterPassword);
        }
    }

    void lockVault() {
        SecretKey key = this.sessionKey;
        if (key != null) {
            try {
                byte[] encoded = key.getEncoded();
                if (encoded != null) {
                    Arrays.fill(encoded, (byte) 0);
                }
            } catch (Exception ignored) {
            }
        }
        this.sessionKey = null;
        this.cachedMetadata = null;
        cancelAutoLockTimer();
    }

    boolean isUnlocked() {
        return sessionKey != null;
    }

    VaultMetadata getMetadata() {
        return cachedMetadata;
    }

    SecretKey getSessionKeyForServices() {
        return sessionKey;
    }

    void rotateVault(char[] oldMasterPassword, char[] newMasterPassword, String newKdfParamsJson) {
        throw new UnsupportedOperationException("Not implemented yet");
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
