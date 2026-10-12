package com.doof.passwordmanager.ui.tui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `doofv --help` / `-h` must print usage to stdout, exit 0, and not open (or create) a vault. */
class TuiAppHelpTest {

    @TempDir
    Path tmp;

    @Test
    void helpPrintsUsageToStdoutAndReturnsNormally() {
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path vault = tmp.resolve("vault.db");
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            for (String flag : new String[]{"--help", "-h"}) {
                out.reset();
                // Returns instead of calling System.exit, which means exit status 0.
                TuiApp.main(new String[]{"--vault", vault.toString(), flag});
                assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("usage: doofv "), out.toString(StandardCharsets.UTF_8));
            }
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        assertEquals("", err.toString(StandardCharsets.UTF_8));
        assertFalse(Files.exists(vault), "help must not create a vault");
    }
}
