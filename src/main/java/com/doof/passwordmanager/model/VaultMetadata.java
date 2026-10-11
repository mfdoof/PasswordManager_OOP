package com.doof.passwordmanager.model;

import java.time.LocalDateTime;

public class VaultMetadata {
    private int id;
    private byte[] kdfSalt;
    private String kdfParams;
    private byte[] masterHash;
    private int encVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VaultMetadata() {
    }

    public VaultMetadata(int id, byte[] kdfSalt, String kdfParams, byte[] masterHash, int encVersion,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.kdfSalt = kdfSalt;
        this.kdfParams = kdfParams;
        this.masterHash = masterHash;
        this.encVersion = encVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public VaultMetadata(byte[] kdfSalt, String kdfParams, byte[] masterHash, int encVersion) {
        this.kdfSalt = kdfSalt;
        this.kdfParams = kdfParams;
        this.masterHash = masterHash;
        this.encVersion = encVersion;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public byte[] getMasterHash() {
        return masterHash;
    }

    public void setMasterHash(byte[] masterHash) {
        this.masterHash = masterHash;
    }

    public int getEncVersion() {
        return encVersion;
    }

    public void setEncVersion(int encVersion) {
        this.encVersion = encVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return String.format(
                "VaultMetadata{id=%d, encVersion=%d, createdAt=%s, updatedAt=%s}",
                id, encVersion, createdAt, updatedAt
        );
    }
}
