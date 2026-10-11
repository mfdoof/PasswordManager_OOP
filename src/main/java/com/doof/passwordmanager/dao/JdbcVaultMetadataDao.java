package com.doof.passwordmanager.dao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.VaultMetadata;

import java.sql.*;
import java.time.LocalDateTime;

public class JdbcVaultMetadataDao implements VaultMetadataDao {
    @Override
    public void insert(VaultMetadata metadata){
        String sql = "INSERT INTO vault_metadata (kdf_salt, kdf_params, master_hash, enc_version) VALUES (?, ?, ?, ?)";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBytes(1, metadata.getKdfSalt());
            ps.setString(2, metadata.getKdfParams());
            ps.setBytes(3, metadata.getMasterHash());
            ps.setInt(4, metadata.getEncVersion());

            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new RuntimeException("Inserting vault metadata failed, no rows affected.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting vault metadata", e);
        }
    }

    @Override
    public VaultMetadata find() {
        String sql = "SELECT * FROM vault_metadata WHERE id = 1";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                VaultMetadata metadata = new VaultMetadata();
                metadata.setId(rs.getInt("id"));
                metadata.setKdfSalt(rs.getBytes("kdf_salt"));
                metadata.setKdfParams(rs.getString("kdf_params"));
                metadata.setMasterHash(rs.getBytes("master_hash"));
                metadata.setEncVersion(rs.getInt("enc_version"));

                Timestamp createdTs = rs.getTimestamp("created_at");
                Timestamp updatedTs = rs.getTimestamp("updated_at");
                if (createdTs != null) metadata.setCreatedAt(createdTs.toLocalDateTime());
                if (updatedTs != null) metadata.setUpdatedAt(updatedTs.toLocalDateTime());

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
                "SET kdf_salt=?, kdf_params=?, master_hash=?, enc_version=?, " +
                "updated_at=CURRENT_TIMESTAMP " +
                "WHERE id = 1";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBytes(1, metadata.getKdfSalt());
            ps.setString(2, metadata.getKdfParams());
            ps.setBytes(3, metadata.getMasterHash());
            ps.setInt(4, metadata.getEncVersion());

            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new RuntimeException("Updating vault metadata failed, no rows affected.");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error updating vault metadata", e);
        }
    }

}
