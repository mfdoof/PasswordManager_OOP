package com.doof.passwordmanager.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Opens the vault's SQLite file. The vault file lives outside the repo, is created
 * with owner-only permissions, and needs no credentials (SQLite has no DB users).
 */
public final class ConnectionManager {

    public static final String VAULT_ARG = "--vault";
    public static final String VAULT_ENV = "DOOF_VAULT_PATH";
    public static final int SCHEMA_VERSION = 2;

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private final Path vaultPath;
    private final String url;

    private ConnectionManager(Path vaultPath) {
        this.vaultPath = vaultPath;
        this.url = "jdbc:sqlite:" + vaultPath;
    }

    /** Opens the vault at {@code vaultPath}, printing permission warnings to stderr. */
    public static ConnectionManager open(Path vaultPath) {
        return open(vaultPath, msg -> System.err.println("WARNING: " + msg));
    }

    /**
     * Opens (creating if needed) the vault at {@code vaultPath}: creates the directory (700)
     * and file (600) if missing, warns if group/other have access, and applies the schema on first run.
     */
    public static ConnectionManager open(Path vaultPath, Consumer<String> warn) {
        Objects.requireNonNull(warn);
        Path path = vaultPath.toAbsolutePath().normalize();
        try {
            prepareFile(path, warn);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare vault file " + path, e);
        }
        ConnectionManager cm = new ConnectionManager(path);
        try {
            cm.initSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialize vault schema in " + path, e);
        }
        return cm;
    }

    /** Vault path from {@code --vault <path>}, then {@code DOOF_VAULT_PATH}, else {@code ~/.doof/vault.db}. */
    public static Path resolveVaultPath(String[] args) {
        return resolveVaultPath(args, System.getenv(), System.getProperty("user.home"));
    }

    /** Same as {@link #resolveVaultPath(String[])} with explicit environment and home (CLI, tests). */
    public static Path resolveVaultPath(String[] args, Map<String, String> env, String userHome) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (VAULT_ARG.equals(args[i])) {
                    if (i + 1 >= args.length || args[i + 1].isBlank()) {
                        throw new IllegalArgumentException(VAULT_ARG + " requires a path");
                    }
                    return Paths.get(args[i + 1]);
                }
                if (args[i].startsWith(VAULT_ARG + "=")) {
                    String value = args[i].substring(VAULT_ARG.length() + 1);
                    if (value.isBlank()) {
                        throw new IllegalArgumentException(VAULT_ARG + " requires a path");
                    }
                    return Paths.get(value);
                }
            }
        }
        String fromEnv = env.get(VAULT_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv);
        }
        return Paths.get(userHome, ".doof", "vault.db");
    }

    public Path getVaultPath() {
        return vaultPath;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            // Overwrite deleted content instead of leaving it in free pages.
            st.execute("PRAGMA secure_delete = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    private static void prepareFile(Path path, Consumer<String> warn) throws IOException {
        PrivateFiles.ensureDirectory(path.getParent(), "Vault directory", warn);
        if (!Files.exists(path)) {
            PrivateFiles.createFile(path);
        } else {
            PrivateFiles.warnIfLoose(path, "Vault file", "600", warn);
        }
    }

    private void initSchema() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            int version;
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                version = rs.next() ? rs.getInt(1) : 0;
            }
            if (version == SCHEMA_VERSION) {
                return;
            }
            if (version != 0) {
                throw new SQLException("Unsupported vault schema version " + version
                        + " (this build supports " + SCHEMA_VERSION + ")");
            }

            conn.setAutoCommit(false);
            try {
                for (String sql : loadSchema().split(";")) {
                    if (!sql.isBlank()) st.execute(sql);
                }
                st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static String loadSchema() {
        try (InputStream in = ConnectionManager.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing schema resource " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
