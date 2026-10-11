package com.doof.passwordmanager.dao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.VaultMetadata;

import java.sql.*;
import java.time.Clock;
import java.util.Objects;

public class JdbcVaultMetadataDao implements VaultMetadataDao {

    private final ConnectionManager connectionManager;
    private final Clock clock;

    public JdbcVaultMetadataDao(ConnectionManager connectionManager) {
        this(connectionManager, Clock.systemUTC());
    }

    public JdbcVaultMetadataDao(ConnectionManager connectionManager, Clock clock) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void insert(VaultMetadata metadata){
        String sql = "INSERT INTO vault_metadata " +
                "(id, kdf_salt, kdf_params, auth_hash, enc_version, failed_attempts, last_failed_at, created_at, updated_at) " +
                "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)";
        long now = clock.millis();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBytes(1, metadata.getKdfSalt());
            ps.setString(2, metadata.getKdfParams());
            ps.setBytes(3, metadata.getAuthHash());
            ps.setInt(4, metadata.getEncVersion());
            ps.setInt(5, metadata.getFailedAttempts());
            setNullableLong(ps, 6, metadata.getLastFailedAt());
            ps.setLong(7, now);
            ps.setLong(8, now);

            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new RuntimeException("Inserting vault metadata failed, no rows affected.");
            }
            metadata.setCreatedAt(now);
            metadata.setUpdatedAt(now);

        } catch (SQLException e) {
            throw new RuntimeException("Error inserting vault metadata", e);
        }
    }

    @Override
    public VaultMetadata find() {
        String sql = "SELECT kdf_salt, kdf_params, auth_hash, enc_version, failed_attempts, last_failed_at, " +
                "created_at, updated_at FROM vault_metadata WHERE id = 1";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                VaultMetadata metadata = new VaultMetadata();
                metadata.setKdfSalt(rs.getBytes("kdf_salt"));
                metadata.setKdfParams(rs.getString("kdf_params"));
                metadata.setAuthHash(rs.getBytes("auth_hash"));
                metadata.setEncVersion(rs.getInt("enc_version"));
                metadata.setFailedAttempts(rs.getInt("failed_attempts"));
                long lastFailedAt = rs.getLong("last_failed_at");
                metadata.setLastFailedAt(rs.wasNull() ? null : lastFailedAt);
                metadata.setCreatedAt(rs.getLong("created_at"));
                metadata.setUpdatedAt(rs.getLong("updated_at"));
                return metadata;
            }

            return null; // no vault created yet

        } catch (SQLException e) {
            throw new RuntimeException("Error reading vault metadata", e);
        }
    }

    @Override
    public void update(VaultMetadata metadata){
        String sql = "UPDATE vault_metadata " +
                "SET kdf_salt=?, kdf_params=?, auth_hash=?, enc_version=?, " +
                "failed_attempts=?, last_failed_at=?, updated_at=? " +
                "WHERE id = 1";
        long now = clock.millis();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBytes(1, metadata.getKdfSalt());
            ps.setString(2, metadata.getKdfParams());
            ps.setBytes(3, metadata.getAuthHash());
            ps.setInt(4, metadata.getEncVersion());
            ps.setInt(5, metadata.getFailedAttempts());
            setNullableLong(ps, 6, metadata.getLastFailedAt());
            ps.setLong(7, now);

            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new RuntimeException("Updating vault metadata failed, no rows affected.");
            }
            metadata.setUpdatedAt(now);

        } catch (SQLException e) {
            throw new RuntimeException("Error updating vault metadata", e);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }
}
