package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.model.VaultMetadata;

import java.sql.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class JdbcVaultRotationDao implements VaultRotationDao {

    private final ConnectionManager connectionManager;
    private final Clock clock;

    public JdbcVaultRotationDao(ConnectionManager connectionManager) {
        this(connectionManager, Clock.systemUTC());
    }

    public JdbcVaultRotationDao(ConnectionManager connectionManager, Clock clock) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void rotate(Function<List<Entry>, Rotation> reencrypt) {
        String selectEntries = "SELECT id, data_enc, iv, enc_version, created_at, updated_at FROM entries ORDER BY id ASC";
        // Entry updated_at is left alone: rotation does not change entry contents.
        String updateEntry = "UPDATE entries SET data_enc = ?, iv = ?, enc_version = ? WHERE id = ?";
        String updateMeta = "UPDATE vault_metadata SET kdf_salt = ?, kdf_params = ?, auth_hash = ?, enc_version = ?, " +
                "failed_attempts = 0, last_failed_at = NULL, updated_at = ? WHERE id = 1";
        long now = clock.millis();

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Take the write lock up front so no other connection can change entries mid-rotation.
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("UPDATE vault_metadata SET id = id WHERE id = 1");
                }

                List<Entry> loaded = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectEntries);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Entry e = new Entry(rs.getBytes("data_enc"), rs.getBytes("iv"), rs.getInt("enc_version"));
                        e.setId(rs.getLong("id"));
                        e.setCreatedAt(rs.getLong("created_at"));
                        e.setUpdatedAt(rs.getLong("updated_at"));
                        loaded.add(e);
                    }
                }

                Rotation rotation = Objects.requireNonNull(reencrypt.apply(loaded), "reencrypt returned null");
                checkSameIds(loaded, rotation.entries);

                try (PreparedStatement ps = conn.prepareStatement(updateEntry)) {
                    for (Entry e : rotation.entries) {
                        ps.setBytes(1, e.getDataEnc());
                        ps.setBytes(2, e.getIv());
                        ps.setInt(3, e.getEncVersion());
                        ps.setLong(4, e.getId());
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("Rotation failed: entry " + e.getId() + " not updated.");
                        }
                    }
                }

                VaultMetadata m = rotation.metadata;
                try (PreparedStatement ps = conn.prepareStatement(updateMeta)) {
                    ps.setBytes(1, m.getKdfSalt());
                    ps.setString(2, m.getKdfParams());
                    ps.setBytes(3, m.getAuthHash());
                    ps.setInt(4, m.getEncVersion());
                    ps.setLong(5, now);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Rotation failed: vault metadata not updated.");
                    }
                }

                conn.commit();
                m.setFailedAttempts(0);
                m.setLastFailedAt(null);
                m.setUpdatedAt(now);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error rotating vault (rolled back)", e);
        }
    }

    private static void checkSameIds(List<Entry> loaded, List<Entry> rotated) {
        Set<Long> expected = new HashSet<>();
        for (Entry e : loaded) expected.add(e.getId());
        Set<Long> actual = new HashSet<>();
        for (Entry e : rotated) actual.add(e.getId());
        if (rotated.size() != loaded.size() || !expected.equals(actual)) {
            throw new IllegalStateException("Rotation must re-encrypt exactly the stored entries");
        }
    }
}
