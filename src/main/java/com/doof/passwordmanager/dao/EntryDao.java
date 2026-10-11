package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.model.Entry;
import java.util.List;
import java.util.function.LongFunction;

/** Stores and loads entries as opaque encrypted bytes. Never sees plaintext. */
public interface EntryDao {
    /**
     * Inserts a new entry whose ciphertext depends on its id (the id is the AES-GCM AAD).
     * In one transaction: allocates the id, calls {@code seal} with it to get the encrypted
     * blob, IV and enc version, and stores them. Returns the stored entry with id and timestamps set.
     * If {@code seal} throws, nothing is stored.
     */
    Entry insert(LongFunction<Entry> seal);
    Entry findById(long id);
    List<Entry> findAll();
    /** Replaces the encrypted blob, IV and enc version; sets the new updatedAt. */
    void update(Entry entry);
    void delete(long id);
}
