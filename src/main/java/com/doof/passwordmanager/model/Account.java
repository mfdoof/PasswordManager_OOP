package com.doof.passwordmanager.model;

import java.time.LocalDateTime;

public class Account {
    private int id;
    private String email;
    private String website;

    private byte[] passwordEncrypted;
    private byte[] iv;
    private int encVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Account() {
    }

    public Account(int id, String email, String website, byte[] passwordEncrypted, byte[] iv,
                   int encVersion, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.website = website;
        this.passwordEncrypted = passwordEncrypted;
        this.iv = iv;
        this.encVersion = encVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Account(String email, String website, byte[] passwordEncrypted, byte[] iv, int encVersion) {
        this.email = email;
        this.website = website;
        this.passwordEncrypted = passwordEncrypted;
        this.iv = iv;
        this.encVersion = encVersion;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public byte[] getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public void setPasswordEncrypted(byte[] passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
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
                "Account{id=%d, email='%s', website='%s', encVersion=%d, createdAt=%s, updatedAt=%s}",
                id, email, website, encVersion, createdAt, updatedAt
        );
    }
}
