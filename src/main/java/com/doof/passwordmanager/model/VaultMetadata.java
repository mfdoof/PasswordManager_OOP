package com.doof.passwordmanager.model;

/** The single vault_metadata row: KDF inputs, auth hash, and persisted unlock-failure state. */
public class VaultMetadata {
    private byte[] kdfSalt;
    private String kdfParams;
    private byte[] authHash;
    private int encVersion;
    private int failedAttempts;
    private Long lastFailedAt;  // epoch ms, null if never failed
    private long createdAt;     // epoch ms
    private long updatedAt;     // epoch ms

    public VaultMetadata() {
    }

    public VaultMetadata(byte[] kdfSalt, String kdfParams, byte[] authHash, int encVersion) {
        this.kdfSalt = kdfSalt;
        this.kdfParams = kdfParams;
        this.authHash = authHash;
        this.encVersion = encVersion;
    }

    public byte[] getKdfSalt() {
        return kdfSalt;
    }

    public void setKdfSalt(byte[] kdfSalt) {
        this.kdfSalt = kdfSalt;
    }

    public String getKdfParams() {
        return kdfParams;
    }

    public void setKdfParams(String kdfParams) {
        this.kdfParams = kdfParams;
    }

    public byte[] getAuthHash() {
        return authHash;
    }

    public void setAuthHash(byte[] authHash) {
        this.authHash = authHash;
    }

    public int getEncVersion() {
        return encVersion;
    }

    public void setEncVersion(int encVersion) {
        this.encVersion = encVersion;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Long getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(Long lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return String.format(
                "VaultMetadata{encVersion=%d, failedAttempts=%d, createdAt=%d, updatedAt=%d}",
                encVersion, failedAttempts, createdAt, updatedAt
        );
    }
}
