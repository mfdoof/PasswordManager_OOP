package com.doof.passwordmanager.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConnectionManagerTest {

    @TempDir
    Path tmp;

    // --- path resolution ---

    @Test
    void vaultArgWinsOverEnv() {
        Path p = ConnectionManager.resolveVaultPath(
                new String[]{"--vault", "/a/arg.db"}, Map.of("DOOF_VAULT_PATH", "/b/env.db"), "/home/u");
        assertEquals(Paths.get("/a/arg.db"), p);
    }

    @Test
    void vaultArgWithEqualsSign() {
        Path p = ConnectionManager.resolveVaultPath(new String[]{"--vault=/a/arg.db"}, Map.of(), "/home/u");
        assertEquals(Paths.get("/a/arg.db"), p);
    }

    @Test
    void envUsedWhenNoArg() {
        Path p = ConnectionManager.resolveVaultPath(new String[]{}, Map.of("DOOF_VAULT_PATH", "/b/env.db"), "/home/u");
        assertEquals(Paths.get("/b/env.db"), p);
    }

    @Test
    void defaultsToHomeDoofVault() {
        assertEquals(Paths.get("/home/u/.doof/vault.db"),
                ConnectionManager.resolveVaultPath(null, Map.of(), "/home/u"));
        assertEquals(Paths.get("/home/u/.doof/vault.db"),
                ConnectionManager.resolveVaultPath(new String[]{}, Map.of("DOOF_VAULT_PATH", "  "), "/home/u"));
    }

    @Test
    void homeComesFromTheHomeVariableWhenSet() {
        assertEquals("/tmp/other-home", ConnectionManager.userHome(Map.of("HOME", "/tmp/other-home")));
        assertEquals(System.getProperty("user.home"), ConnectionManager.userHome(Map.of()));
        assertEquals(System.getProperty("user.home"), ConnectionManager.userHome(Map.of("HOME", " ")));
    }

    @Test
    void vaultArgWithoutValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConnectionManager.resolveVaultPath(new String[]{"--vault"}, Map.of(), "/home/u"));
        assertThrows(IllegalArgumentException.class,
                () -> ConnectionManager.resolveVaultPath(new String[]{"--vault="}, Map.of(), "/home/u"));
    }

    // --- file creation, permissions, schema ---

    @Test
    void createsDirAndFileWithOwnerOnlyPermissions() throws Exception {
        assumePosix();
        Path vault = tmp.resolve("newdir/vault.db");
        List<String> warnings = new ArrayList<>();

        ConnectionManager.open(vault, warnings::add);

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(vault.getParent())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(vault)));
        assertTrue(warnings.isEmpty(), () -> "unexpected warnings: " + warnings);
    }

    @Test
    void appliesSchemaOnFirstRunOnly() throws Exception {
        Path vault = tmp.resolve("vault.db");
        ConnectionManager cm = ConnectionManager.open(vault, w -> {});

        try (Connection conn = cm.getConnection(); Statement st = conn.createStatement()) {
            assertEquals(ConnectionManager.SCHEMA_VERSION, queryInt(st, "PRAGMA user_version"));
            assertEquals(2, queryInt(st,
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('vault_metadata','entries')"));
            st.execute("INSERT INTO entries (data_enc, iv, enc_version, created_at, updated_at) VALUES (x'01', x'02', 1, 0, 0)");
        }

        // Reopening must not re-run the schema (CREATE TABLE would fail) or lose data.
        ConnectionManager reopened = ConnectionManager.open(vault, w -> {});
        try (Connection conn = reopened.getConnection(); Statement st = conn.createStatement()) {
            assertEquals(1, queryInt(st, "SELECT count(*) FROM entries"));
        }
    }

    @Test
    void enablesSecureDelete() throws Exception {
        ConnectionManager cm = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        try (Connection conn = cm.getConnection(); Statement st = conn.createStatement()) {
            assertEquals(1, queryInt(st, "PRAGMA secure_delete"));
        }
    }

    @Test
    void rejectsNewerSchemaVersion() throws Exception {
        Path vault = tmp.resolve("vault.db");
        ConnectionManager cm = ConnectionManager.open(vault, w -> {});
        try (Connection conn = cm.getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = 99");
        }
        assertThrows(IllegalStateException.class, () -> ConnectionManager.open(vault, w -> {}));
    }

    @Test
    void warnsWhenFilePermissionsAreLoose() throws Exception {
        assumePosix();
        Path vault = tmp.resolve("d/vault.db");
        ConnectionManager.open(vault, w -> {});
        Files.setPosixFilePermissions(vault, PosixFilePermissions.fromString("rw-r--r--"));

        List<String> warnings = new ArrayList<>();
        ConnectionManager.open(vault, warnings::add);

        assertEquals(1, warnings.size(), () -> "warnings: " + warnings);
        assertTrue(warnings.get(0).contains("chmod 600"));
    }

    @Test
    void warnsWhenDirectoryPermissionsAreLoose() throws Exception {
        assumePosix();
        Path vault = tmp.resolve("d/vault.db");
        ConnectionManager.open(vault, w -> {});
        Files.setPosixFilePermissions(vault.getParent(), PosixFilePermissions.fromString("rwxr-x---"));

        List<String> warnings = new ArrayList<>();
        ConnectionManager.open(vault, warnings::add);

        assertEquals(1, warnings.size(), () -> "warnings: " + warnings);
        assertTrue(warnings.get(0).contains("chmod 700"));
    }

    private static int queryInt(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static void assumePosix() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "POSIX permissions only");
    }
}
