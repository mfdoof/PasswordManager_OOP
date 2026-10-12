package com.doof.passwordmanager.backup;

import com.doof.passwordmanager.db.ConnectionManager;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Map;

/**
 * {@code doofv backup}: non-interactive, for cron / launchd / systemd. Plain one-line output.
 * Exit codes: 0 backup written and verified, 1 backup failed, 2 usage error.
 */
public final class BackupCommand {

    public static final int OK = 0;
    public static final int FAILED = 1;
    public static final int USAGE = 2;

    public static final String USAGE_TEXT = String.join("\n",
            "usage: doofv backup [--vault <path>] [--out <dir>] [--keep N]",
            "  --vault <path>  vault to back up (default: $DOOF_VAULT_PATH, then ~/.doof/vault.db)",
            "  --out <dir>     where backups go (default: ~/.doof/backups, created as 700)",
            "  --keep N        keep the newest N backups, delete older ones (default: " + VaultBackup.DEFAULT_KEEP + ")",
            "Copies the encrypted vault file (no master password needed), verifies the copy,",
            "writes vault-YYYYMMDD-HHMMSS.db (600), then prunes.",
            "  -h, --help      show this help",
            "Exit codes: 0 ok, 1 backup failed (nothing written or pruned), 2 usage error.");

    private BackupCommand() {}

    public static int run(String[] args, Map<String, String> env, String userHome, Clock clock,
                          PrintStream out, PrintStream err) {
        String vaultArg = null;
        String outArg = null;
        int keep = VaultBackup.DEFAULT_KEEP;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a.equals("-h") || a.equals("--help")) {
                    out.println(USAGE_TEXT);
                    return OK;
                } else if (a.equals("--vault") || a.equals("--out") || a.equals("--keep")) {
                    if (i + 1 >= args.length || args[i + 1].isBlank()) {
                        throw new IllegalArgumentException(a + " requires a value");
                    }
                    String v = args[++i];
                    if (a.equals("--vault")) vaultArg = v;
                    else if (a.equals("--out")) outArg = v;
                    else keep = parseKeep(v);
                } else if (a.startsWith("--vault=")) {
                    vaultArg = nonBlank(a, a.substring(8));
                } else if (a.startsWith("--out=")) {
                    outArg = nonBlank(a, a.substring(6));
                } else if (a.startsWith("--keep=")) {
                    keep = parseKeep(a.substring(7));
                } else {
                    throw new IllegalArgumentException("unknown argument: " + a);
                }
            }
        } catch (IllegalArgumentException e) {
            err.println("doofv backup: " + e.getMessage());
            err.println(USAGE_TEXT);
            return USAGE;
        }

        Path vault = ConnectionManager.resolveVaultPath(
                vaultArg == null ? new String[0] : new String[]{"--vault", vaultArg}, env, userHome);
        Path outDir = outArg == null ? Paths.get(userHome, ".doof", "backups") : Paths.get(outArg);

        try {
            VaultBackup.Result r = new VaultBackup(clock, w -> err.println("backup: warning: " + w))
                    .run(vault, outDir, keep);
            out.println("backup: wrote " + r.file + " (" + r.bytes + " bytes, verified)");
            for (Path p : r.pruned) {
                out.println("backup: pruned " + p);
            }
            out.println("backup: " + r.kept + " backup(s) kept in " + r.file.getParent());
            return OK;
        } catch (VaultBackup.BackupException e) {
            err.println("backup: error: " + e.getMessage());
            return FAILED;
        }
    }

    private static int parseKeep(String v) {
        try {
            int n = Integer.parseInt(v.trim());
            if (n < 1) throw new IllegalArgumentException("--keep must be at least 1");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--keep must be a number: " + v);
        }
    }

    private static String nonBlank(String arg, String value) {
        if (value.isBlank()) throw new IllegalArgumentException(arg + " requires a value");
        return value;
    }
}
