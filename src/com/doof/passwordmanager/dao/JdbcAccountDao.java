package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.model.Account;
import com.doof.passwordmanager.db.ConnectionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAccountDao implements AccountDao {

    @Override
    public void addAccount(Account account) {
        String sql = "INSERT INTO accounts (email, password_encrypted, iv, enc_version, website) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, account.getEmail());
            ps.setBytes(2, account.getPasswordEncrypted());
            ps.setBytes(3, account.getIv());
            ps.setInt(4, account.getEncVersion());
            ps.setString(5, account.getWebsite());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting account failed, no rows affected.");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    account.setId(rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error inserting account", e);
        }
    }

    @Override
    public List<Account> getAllAccounts() {
        String sql = "SELECT id, email, password_encrypted, iv, enc_version, website, created_at, updated_at FROM accounts";
        List<Account> accounts = new ArrayList<>();

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                accounts.add(mapRowToAccount(rs));
            }

            return accounts;

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching all accounts", e);
        }
    }

    @Override
    public Account getAccountById(int id) {
        String sql = "SELECT id, email, password_encrypted, iv, enc_version, website, created_at, updated_at FROM accounts WHERE id = ?";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToAccount(rs);
                } else {
                    return null;
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching account by id: " + id, e);
        }
    }

    @Override
    public void updateAccount(Account account) {
        String sql = "UPDATE accounts SET email = ?, password_encrypted = ?, iv = ?, enc_version = ?, website = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, account.getEmail());
            ps.setBytes(2, account.getPasswordEncrypted());
            ps.setBytes(3, account.getIv());
            ps.setInt(4, account.getEncVersion());
            ps.setString(5, account.getWebsite());
            ps.setInt(6, account.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Update failed: account with id " + account.getId() + " does not exist.");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error updating account with id: " + account.getId(), e);
        }
    }

    @Override
    public void deleteAccount(int id) {
        String sql = "DELETE FROM accounts WHERE id = ?";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Delete failed: account with id " + id + " does not exist.");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting account with id: " + id, e);
        }
    }

    @Override
    public List<Account> search(String query, int limit, int offset) {
        String sql = "SELECT id, email, password_encrypted, iv, enc_version, website, created_at, updated_at " +
                "FROM accounts " +
                "WHERE email LIKE ? OR website LIKE ? " +
                "ORDER BY id ASC " +
                "LIMIT ? OFFSET ?";
        List<Account> accounts = new ArrayList<>();
        String like = "%" + query + "%";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, like);
            ps.setString(2, like);
            ps.setInt(3, limit);
            ps.setInt(4, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapRowToAccount(rs));
                }
            }

            return accounts;

        } catch (SQLException e) {
            throw new RuntimeException("Error searching accounts", e);
        }
    }

    private Account mapRowToAccount(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setId(rs.getInt("id"));
        account.setEmail(rs.getString("email"));
        account.setPasswordEncrypted(rs.getBytes("password_encrypted"));
        account.setIv(rs.getBytes("iv"));
        account.setEncVersion(rs.getInt("enc_version"));
        account.setWebsite(rs.getString("website"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        if (createdTs != null) account.setCreatedAt(createdTs.toLocalDateTime());
        if (updatedTs != null) account.setUpdatedAt(updatedTs.toLocalDateTime());

        return account;
    }
}
