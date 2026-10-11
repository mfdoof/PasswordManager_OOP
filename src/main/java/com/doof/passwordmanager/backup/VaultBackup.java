package com.doof.passwordmanager.backup;

import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.db.PrivateFiles;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConnection;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copies the encrypted vault file with SQLite's online backup API, verifies the copy, then prunes
 * old copies. Works on ciphertext only: no master password, no keys. The copy is a consistent
 * snapshot of committed data even while the app has the vault open.
 */
public final class VaultBackup {

    public static final int DEFAULT_KEEP = 14;

    /** Backup names this tool creates (and is allowed to prune): vault-YYYYMMDD-HHMMSS[-N].db */
    static final Pattern NAME = Pattern.compile("vault-(\\d{8}-\\d{6})(?:-(\\d+))?\\.db");
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // sqlite3_backup: copy all pages in one step (consistent snapshot); retry while the source is busy.
    private static final int ALL_PAGES = -1;
    private static final int BUSY_SLEEP_MS = 100;
    private static final int BUSY_RETRIES = 50;
    private static final int SQLITE_OK = 0;
    private static final int SQLITE_DONE = 101;

    /** Outcome of a successful run. */
    public static final class Result {
        public final Path file;
        public final long bytes;
        public final List<Path> pruned;
        public final int kept;

        Result(Path file, long bytes, List<Path> pruned, int kept) {
            this.file = file;
            this.bytes = bytes;
            this.pruned = pruned;
            this.kept = kept;
        }
    }

    /** The backup failed; no new file was left behind and nothing was pruned. */
    public static final class BackupException extends Exception {
        BackupException(String message) {
            super(message);
        }

        BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Clock clock;
    private final Consumer<String> warn;

    /** {@code clock} (with its zone) names the file; {@code warn} receives permission warnings. */
    public VaultBackup(Clock clock, Consumer<String> warn) {
        this.clock = Objects.requireNonNull(clock);
        this.warn = Objects.requireNonNull(warn);
    }

    public Result run(Path vault, Path outDir, int keep) throws BackupException {
        if (keep < 1) throw new IllegalArgumentException("keep must be at least 1");
        vault = vault.toAbsolutePath().normalize();
        outDir = outDir.toAbsolutePath().normalize();
        if (!Files.isRegularFile(vault)) {
            throw new BackupException("no vault at " + vault);  // never create one here
        }

        Path target;
        Path partial;
        try {
            PrivateFiles.ensureDirectory(outDir, "Backup directory", warn);
            target = freeName(outDir);
            partial = target.resolveSibling(target.getFileName() + ".partial");
            PrivateFiles.createFile(partial);  // 600 before SQLite writes a single byte
        } catch (IOException e) {
            throw new BackupException("cannot prepare " + outDir + ": " + e.getMessage(), e);
        }

        try {
            copy(vault, partial);
            verify(partial);
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (BackupException | IOException | RuntimeException e) {
            deleteQuietly(partial);
            deleteQuietly(partial.resolveSibling(partial.getFileName() + "-journal"));
            if (e instanceof BackupException be) throw be;
            throw new BackupException("backup failed: " + e.getMessage(), e);
        }

        try {
            long bytes = Files.size(target);
            List<Path> pruned = prune(outDir, keep);
            return new Result(target, bytes, pruned, Math.min(keep, countBackups(outDir)));
        } catch (IOException e) {
            throw new BackupException("backup written to " + target + " but pruning failed: " + e.getMessage(), e);
        }
    }

    private Path freeName(Path outDir) {
        String stamp = LocalDateTime.now(clock).format(STAMP);
        Path p = outDir.resolve("vault-" + stamp + ".db");
        for (int n = 2; Files.exists(p) || Files.exists(p.resolveSibling(p.getFileName() + ".partial")); n++) {
            p = outDir.resolve("vault-" + stamp + "-" + n + ".db");
        }
        return p;
    }

    private static void copy(Path vault, Path dest) throws BackupException {
        try (Connection conn = openReadOnly(vault)) {
            int rc = conn.unwrap(SQLiteConnection.class).getDatabase()
                    .backup("main", dest.toString(), null, BUSY_SLEEP_MS, BUSY_RETRIES, ALL_PAGES);
            if (rc != SQLITE_OK && rc != SQLITE_DONE) {
                throw new BackupException("SQLite backup failed (code " + rc + ")");
            }
        } catch (SQLException e) {
            throw new BackupException("cannot read vault: " + e.getMessage(), e);
        }
    }

    /** The copy must be a sound, current-format, set-up vault. */
    static void verify(Path copy) throws BackupException {
        try (Connection conn = openReadOnly(copy); Statement st = conn.createStatement()) {
            String integrity = queryString(st, "PRAGMA integrity_check");
            if (!"ok".equals(integrity)) {
                throw new BackupException("verify failed: integrity_check returned " + integrity);
            }
            int version = queryInt(st, "PRAGMA user_version");
            if (version != ConnectionManager.SCHEMA_VERSION) {
                throw new BackupException("verify failed: schema version " + version
                        + ", expected " + ConnectionManager.SCHEMA_VERSION + " (is this a DOOF vault?)");
            }
            if (queryInt(st, "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'vault_metadata'") != 1) {
                throw new BackupException("verify failed: no vault_metadata table (is this a DOOF vault?)");
            }
            if (queryInt(st, "SELECT count(*) FROM vault_metadata") != 1) {
                throw new BackupException("verify failed: vault has no master password yet (nothing to back up)");
            }
        } catch (SQLException e) {
            throw new BackupException("verify failed: " + e.getMessage(), e);
        }
    }

    /** Deletes our backups beyond the newest {@code keep}; other files in the directory are never touched. */
    static List<Path> prune(Path outDir, int keep) throws IOException {
        List<Path> backups = listBackups(outDir);
        List<Path> pruned = new ArrayList<>();
        for (int i = keep; i < backups.size(); i++) {
            Files.delete(backups.get(i));
            pruned.add(backups.get(i));
        }
        return pruned;
    }

    private static int countBackups(Path outDir) throws IOException {
        return listBackups(outDir).size();
    }

    /** Our backups, newest first (timestamp, then the same-second counter). */
    static List<Path> listBackups(Path outDir) throws IOException {
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(outDir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p) && NAME.matcher(p.getFileName().toString()).matches()) {
                    found.add(p);
                }
            }
        }
        found.sort(Comparator.comparing((Path p) -> stamp(p)).thenComparingInt(VaultBackup::counter).reversed());
        return found;
    }

    private static String stamp(Path p) {
        Matcher m = NAME.matcher(p.getFileName().toString());
        return m.matches() ? m.group(1) : "";
    }

    private static int counter(Path p) {
        Matcher m = NAME.matcher(p.getFileName().toString());
        return m.matches() && m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
    }

    private static Connection openReadOnly(Path file) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setBusyTimeout(5_000);
        return DriverManager.getConnection("jdbc:sqlite:" + file, config.toProperties());
    }

    private static int queryInt(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static String queryString(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
