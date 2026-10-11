package com.doof.passwordmanager.dao;
import  com.doof.passwordmanager.model.VaultMetadata;

public interface VaultMetadataDao {
    /** Inserts the single metadata row and sets its timestamps. Fails if a vault already exists. */
    void insert(VaultMetadata metadata);
    /** Returns the metadata row, or null if no vault has been created yet. */
    VaultMetadata find();
    /** Updates all mutable fields of the metadata row and sets the new updatedAt. */
    void update(VaultMetadata metadata);
}
