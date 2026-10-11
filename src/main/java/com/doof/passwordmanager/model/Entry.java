package com.doof.passwordmanager.model;

/**
 * One stored vault entry as it sits in the database: an opaque encrypted blob plus
 * its IV. All fields (site, user, password, notes) live inside {@code dataEnc}.
 */
public class Entry {
    private long id;
    private byte[] dataEnc;
    private byte[] iv;
    private int encVersion;

    private long createdAt;   // epoch ms
    private long updatedAt;   // epoch ms

    public Entry() {
    }

    public Entry(byte[] dataEnc, byte[] iv, int encVersion) {
        this.dataEnc = dataEnc;
        this.iv = iv;
        this.encVersion = encVersion;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public byte[] getDataEnc() {
        return dataEnc;
    }

    public void setDataEnc(byte[] dataEnc) {
        this.dataEnc = dataEnc;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public int getEncVersion() {
        return encVersion;
    }

    public void setEncVersion(int encVersion) {
        this.encVersion = encVersion;
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
        return String.format("Entry{id=%d, encVersion=%d, createdAt=%d, updatedAt=%d}",
                id, encVersion, createdAt, updatedAt);
    }
}
