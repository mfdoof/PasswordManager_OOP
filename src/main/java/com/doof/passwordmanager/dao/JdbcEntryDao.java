package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;

import java.sql.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

public class JdbcEntryDao implements EntryDao {

    private static final String COLUMNS = "id, data_enc, iv, enc_version, created_at, updated_at";

    private final ConnectionManager connectionManager;
    private final Clock clock;

    public JdbcEntryDao(ConnectionManager connectionManager) {
        this(connectionManager, Clock.systemUTC());
    }

    public JdbcEntryDao(ConnectionManager connectionManager, Clock clock) {
        this.connectionManager = Objects.requireNonNull(connectionManager);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Entry insert(LongFunction<Entry> seal) {
        // Placeholder row reserves the id; it is filled in before commit, so it is never visible.
        String insertSql = "INSERT INTO entries (data_enc, iv, enc_version, created_at, updated_at) VALUES (x'', x'', 0, ?, ?)";
        String fillSql = "UPDATE entries SET data_enc = ?, iv = ?, enc_version = ? WHERE id = ?";
        long now = clock.millis();

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, now);
                    ps.setLong(2, now);
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Inserting entry failed, no rows affected.");
                    }
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("Inserting entry failed, no id returned.");
                        }
                        id = rs.getLong(1);
                    }
                }

                Entry entry = Objects.requireNonNull(seal.apply(id), "seal returned null");
                try (PreparedStatement ps = conn.prepareStatement(fillSql)) {
                    ps.setBytes(1, entry.getDataEnc());
                    ps.setBytes(2, entry.getIv());
                    ps.setInt(3, entry.getEncVersion());
                    ps.setLong(4, id);
                    ps.executeUpdate();
                }
                conn.commit();

                entry.setId(id);
                entry.setCreatedAt(now);
                entry.setUpdatedAt(now);
                return entry;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting entry", e);
        }
    }

    @Override
    public Entry findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM entries WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching entry by id: " + id, e);
        }
    }

    @Override
    public List<Entry> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM entries ORDER BY id ASC";
        List<Entry> entries = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                entries.add(mapRow(rs));
            }
            return entries;

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching all entries", e);
        }
    }

    @Override
    public void update(Entry entry) {
        String sql = "UPDATE entries SET data_enc = ?, iv = ?, enc_version = ?, updated_at = ? WHERE id = ?";
        long now = clock.millis();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBytes(1, entry.getDataEnc());
            ps.setBytes(2, entry.getIv());
            ps.setInt(3, entry.getEncVersion());
            ps.setLong(4, now);
            ps.setLong(5, entry.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Update failed: entry with id " + entry.getId() + " does not exist.");
            }
            entry.setUpdatedAt(now);

        } catch (SQLException e) {
            throw new RuntimeException("Error updating entry with id: " + entry.getId(), e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM entries WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Delete failed: entry with id " + id + " does not exist.");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting entry with id: " + id, e);
        }
    }

    private Entry mapRow(ResultSet rs) throws SQLException {
        Entry entry = new Entry();
        entry.setId(rs.getLong("id"));
        entry.setDataEnc(rs.getBytes("data_enc"));
        entry.setIv(rs.getBytes("iv"));
        entry.setEncVersion(rs.getInt("enc_version"));
        entry.setCreatedAt(rs.getLong("created_at"));
        entry.setUpdatedAt(rs.getLong("updated_at"));
        return entry;
    }
}
