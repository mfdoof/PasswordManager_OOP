package com.doof.passwordmanager.dao;
import  com.doof.passwordmanager.model.VaultMetadata;

import java.lang.foreign.ValueLayout;

public interface VaultMetadataDao {
    void insert(VaultMetadata metadata);
    VaultMetadata find();
    void update(VaultMetadata metadata);
}
