package com.doof.passwordmanager.db;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/** Owner-only files and directories (700 / 600) for the vault and its backups. No-ops off POSIX. */
public final class PrivateFiles {

    static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwx------");
    static final Set<PosixFilePermission> FILE_PERMS = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> GROUP_OTHER = EnumSet.of(
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

    private PrivateFiles() {}

    public static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /** Creates {@code dir} (and parents) with 700 if missing; otherwise warns if group/other have access. */
    public static void ensureDirectory(Path dir, String what, Consumer<String> warn) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            if (posix()) Files.setPosixFilePermissions(dir, DIR_PERMS);
        } else {
            warnIfLoose(dir, what, "700", warn);
        }
    }

    /** Creates an empty file with 600, so nothing (e.g. SQLite) ever creates it with umask-default permissions. */
    public static void createFile(Path file) throws IOException {
        Files.createFile(file);
        if (posix()) Files.setPosixFilePermissions(file, FILE_PERMS);
    }

    public static void warnIfLoose(Path p, String what, String expected, Consumer<String> warn) throws IOException {
        if (!posix()) return;
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(p);
        if (perms.stream().anyMatch(GROUP_OTHER::contains)) {
            warn.accept(String.format("%s %s has permissions %s; group/other can access it. Run: chmod %s %s",
                    what, p, PosixFilePermissions.toString(perms), expected, p));
        }
    }
}
