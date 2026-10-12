package com.doof.passwordmanager.backup;

import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.dao.JdbcVaultRotationDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.service.ApplicationConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BackupCommandTest {

    private static final String MASTER = "Correct9Horse";
    private static final KdfParams FAST = new KdfParams(1024, 1, 1);
    /** 2023-11-14 22:13:20 UTC */
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    private static final String FIRST = "vault-20231114-221320.db";

    @TempDir
    Path tmp;
    private Path home;
    private Path vault;
    private Path out;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        home = tmp.resolve("home");
        vault = tmp.resolve("data/vault.db");
        out = tmp.resolve("backups");
    }

    private static ApplicationConnector connector(Path file) {
        ConnectionManager cm = ConnectionManager.open(file, w -> {});
        return new ApplicationConnector(new JdbcVaultMetadataDao(cm), new JdbcVaultRotationDao(cm),
                new JdbcEntryDao(cm), FAST, Clock.systemUTC());
    }

    private long createVault(Path file) {
        ApplicationConnector c = connector(file);
        c.createVault(MASTER.toCharArray());
        long id = c.addEntry("github.com", "mave", "Hunter2Pass".toCharArray(), "note");
        c.lockVault();
        return id;
    }

    private int run(Map<String, String> env, String... args) {
        stdout.reset();
        stderr.reset();
        return BackupCommand.run(args, env, home.toString(), CLOCK,
                new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private int run(String... args) {
        return run(Map.of(), args);
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    private List<String> names(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    private static int countEntries(Path db) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM entries")) {
            return rs.getInt(1);
        }
    }

    // --- success ---

    @Test
    void writesVerifiedBackupThatUnlocksAndReads() throws Exception {
        long id = createVault(vault);

        assertEquals(BackupCommand.OK, run("--vault", vault.toString(), "--out", out.toString()), err());
        Path copy = out.resolve(FIRST);
        assertTrue(Files.isRegularFile(copy), names(out).toString());
        assertTrue(out().contains("backup: wrote " + copy), out());
        assertTrue(out().contains("verified"));
        assertEquals(List.of(FIRST), names(out), "no .partial or journal left behind");

        ApplicationConnector restored = connector(copy);
        restored.unlockVault(MASTER.toCharArray());
        assertEquals("Hunter2Pass", restored.withPassword(id, String::new));
    }

    @Test
    void fileIs600AndDirectoryIs700() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        createVault(vault);
        assertEquals(BackupCommand.OK, run("--vault", vault.toString(), "--out", out.resolve("new").toString()));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(out.resolve("new"))));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(out.resolve("new/" + FIRST))));
    }

    @Test
    void defaultsToHomeVaultAndHomeBackups() throws Exception {
        createVault(home.resolve(".doof/vault.db"));
        assertEquals(BackupCommand.OK, run(), err());
        assertTrue(Files.exists(home.resolve(".doof/backups/" + FIRST)));
    }

    @Test
    void respectsDoofVaultPathEnv() throws Exception {
        createVault(vault);
        assertEquals(BackupCommand.OK, run(Map.of("DOOF_VAULT_PATH", vault.toString()), "--out", out.toString()), err());
        assertTrue(Files.exists(out.resolve(FIRST)));
    }

    @Test
    void consistentWhileAnotherConnectionHasTheVaultOpenMidWrite() throws Exception {
        createVault(vault);
        ConnectionManager cm = ConnectionManager.open(vault, w -> {});
        try (Connection open = cm.getConnection(); Statement st = open.createStatement()) {
            open.setAutoCommit(false);
            st.executeUpdate("INSERT INTO entries (data_enc, iv, enc_version, created_at, updated_at) "
                    + "VALUES (x'00', x'00', 1, 0, 0)");  // uncommitted write in progress

            assertEquals(BackupCommand.OK, run("--vault", vault.toString(), "--out", out.toString()), err());
            assertEquals(1, countEntries(out.resolve(FIRST)), "backup holds committed data only");

            open.commit();
        }
        assertEquals(2, countEntries(vault));
    }

    // --- pruning ---

    @Test
    void prunesToNewestNAndNeverTouchesOtherFiles() throws Exception {
        createVault(vault);
        Files.createDirectories(out);
        for (int day = 1; day <= 16; day++) {
            Files.createFile(out.resolve(String.format("vault-202301%02d-120000.db", day)));
        }
        Files.writeString(out.resolve("notes.txt"), "mine");
        Files.createFile(out.resolve("vault-old-copy.db"));

        assertEquals(BackupCommand.OK, run("--vault", vault.toString(), "--out", out.toString(), "--keep", "14"));

        List<String> left = names(out);
        assertTrue(left.contains(FIRST), "the new backup is kept");
        assertTrue(left.contains("notes.txt") && left.contains("vault-old-copy.db"), "foreign files untouched");
        long ours = left.stream().filter(n -> VaultBackup.NAME.matcher(n).matches()).count();
        assertEquals(14, ours);
        assertFalse(left.contains("vault-20230103-120000.db"), "oldest pruned");
        assertTrue(left.contains("vault-20230104-120000.db"));
        assertTrue(out().contains("backup: pruned " + out.resolve("vault-20230101-120000.db")), out());
        assertTrue(out().contains("14 backup(s) kept"));
    }

    @Test
    void sameSecondBackupsGetACounterAndPruneInOrder() throws Exception {
        createVault(vault);
        for (int i = 0; i < 3; i++) {
            assertEquals(BackupCommand.OK, run("--vault", vault.toString(), "--out", out.toString(), "--keep", "2"), err());
        }
        assertEquals(List.of("vault-20231114-221320-2.db", "vault-20231114-221320-3.db"), names(out));
    }

    // --- failures: nothing written, nothing pruned ---

    @Test
    void verifyFailureDeletesTheCopyAndPrunesNothing() throws Exception {
        Path notAVault = tmp.resolve("other.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + notAVault); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE something (x)");
            st.execute("PRAGMA user_version = " + ConnectionManager.SCHEMA_VERSION);
        }
        Files.createDirectories(out);
        for (int day = 1; day <= 3; day++) Files.createFile(out.resolve(String.format("vault-202301%02d-120000.db", day)));

        assertEquals(BackupCommand.FAILED, run("--vault", notAVault.toString(), "--out", out.toString(), "--keep", "1"));
        assertTrue(err().contains("verify failed: no vault_metadata table"), err());
        assertEquals(3, names(out).size(), "old backups kept, no partial copy left");
    }

    @Test
    void wrongSchemaVersionFails() throws Exception {
        createVault(vault);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + vault); Statement st = c.createStatement()) {
            st.execute("PRAGMA user_version = 99");
        }
        assertEquals(BackupCommand.FAILED, run("--vault", vault.toString(), "--out", out.toString()));
        assertTrue(err().contains("schema version 99"), err());
        assertTrue(names(out).isEmpty());
    }

    @Test
    void vaultWithoutMasterPasswordFails() throws Exception {
        ConnectionManager.open(vault, w -> {});  // file and schema, but never set up
        assertEquals(BackupCommand.FAILED, run("--vault", vault.toString(), "--out", out.toString()));
        assertTrue(err().contains("no master password yet"), err());
    }

    @Test
    void corruptFileFails() throws Exception {
        Files.createDirectories(vault.getParent());
        Files.write(vault, "this is not a database at all, just some bytes".repeat(200).getBytes());
        assertEquals(BackupCommand.FAILED, run("--vault", vault.toString(), "--out", out.toString()));
        assertTrue(err().startsWith("backup: error:"), err());
        assertTrue(names(out).isEmpty());
    }

    @Test
    void missingVaultFailsAndIsNotCreated() {
        assertEquals(BackupCommand.FAILED, run("--vault", vault.toString(), "--out", out.toString()));
        assertTrue(err().contains("no vault at " + vault), err());
        assertFalse(Files.exists(vault), "backup must never create a vault");
        assertFalse(Files.exists(out));
    }

    // --- usage ---

    @Test
    void usageErrorsExitWithTwo() {
        assertEquals(BackupCommand.USAGE, run("--keep", "0"));
        assertTrue(err().contains("--keep must be at least 1"));
        assertEquals(BackupCommand.USAGE, run("--keep", "lots"));
        assertEquals(BackupCommand.USAGE, run("--out"));
        assertEquals(BackupCommand.USAGE, run("--vault="));
        assertEquals(BackupCommand.USAGE, run("--force"));
        assertTrue(err().contains("unknown argument: --force"));
        assertTrue(err().contains("usage: doofv backup"));
    }

    @Test
    void helpPrintsUsageToStdoutAndExitsZero() {
        for (String flag : new String[]{"--help", "-h"}) {
            assertEquals(BackupCommand.OK, run(flag), flag);
            assertTrue(out().startsWith("usage: doofv backup"), out());
            assertTrue(out().contains("Exit codes: 0 ok, 1 backup failed"), out());
            assertEquals("", err(), "nothing on stderr for " + flag);
        }
        assertEquals(BackupCommand.OK, run("--keep", "3", "-h"), "help wins over other options");
    }
}
