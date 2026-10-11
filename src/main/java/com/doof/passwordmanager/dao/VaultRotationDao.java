package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.model.VaultMetadata;

import java.util.List;
import java.util.function.Function;

/** Master-password rotation as a single all-or-nothing database transaction. */
public interface VaultRotationDao {

    /** New metadata plus every entry re-encrypted under the new key (same ids). */
    final class Rotation {
        public final VaultMetadata metadata;
        public final List<Entry> entries;

        public Rotation(VaultMetadata metadata, List<Entry> entries) {
            this.metadata = metadata;
            this.entries = entries;
        }
    }

    /**
     * In ONE transaction: loads all entries, passes them to {@code reencrypt}, writes back every
     * returned entry (blob, IV, enc version; by id) and replaces the metadata row (salt, params,
     * auth hash, enc version; failed attempts reset). The returned entries must cover exactly the
     * loaded ids. On any failure, including inside {@code reencrypt}, everything is rolled back.
     */
    void rotate(Function<List<Entry>, Rotation> reencrypt);
}
