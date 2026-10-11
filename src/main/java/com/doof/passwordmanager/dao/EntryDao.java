package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.model.Entry;
import java.util.List;

/** Stores and loads entries as opaque encrypted bytes. Never sees plaintext. */
public interface EntryDao {
    /** Inserts the entry and sets its generated id and timestamps. */
    void insert(Entry entry);
    Entry findById(long id);
    List<Entry> findAll();
    /** Replaces the encrypted blob, IV and enc version; sets the new updatedAt. */
    void update(Entry entry);
    void delete(long id);
}
