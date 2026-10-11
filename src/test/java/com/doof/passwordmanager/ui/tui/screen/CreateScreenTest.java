package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.TuiHarness;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateScreenTest {

    private static final String GOOD = "Correct9Horse";

    @TempDir
    Path tmp;
    private TuiHarness h;

    @BeforeEach
    void setUp() {
        h = new TuiHarness(tmp.resolve("vault.db"));
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    @Test
    void newVaultOpensCreateScreenWithPathAndChecklist() {
        assertInstanceOf(CreateScreen.class, h.manager.current());
        String s = h.text();
        assertTrue(s.contains("create vault"), s);
        assertTrue(s.contains("vault: ~/.doof/vault.db"));
        assertTrue(s.contains("[ ] 12+ chars"));
        assertTrue(s.contains("strength [----------] none"));
    }

    @Test
    void inputIsMaskedNeverShownInClear() {
        h.type(GOOD);
        String s = h.text();
        assertTrue(s.contains("> new master password: *************_"), s);
        assertFalse(s.contains(GOOD));
    }

    @Test
    void checklistAndStrengthUpdateLive() {
        h.type("abc");
        String s = h.text();
        assertTrue(s.contains("[ ] 12+ chars  [ ] upper  [x] lower"), s);
        assertTrue(s.contains("[ ] digit      [ ] symbol (optional)"), s);
        assertTrue(s.contains("weak"));

        h.type("DEF123456!");
        s = h.text();
        assertTrue(s.contains("[x] 12+ chars  [x] upper  [x] lower"), s);
        assertTrue(s.contains("[x] digit      [x] symbol (optional)"), s);
        assertTrue(s.contains("strong"), s);
    }

    @Test
    void mismatchShowsInlineAndBlocksCreate() {
        h.type(GOOD);
        h.press(KeyType.Tab);
        h.type("Correct9Horsf");  // same length, different
        assertTrue(h.text().contains("err: passwords do not match"));

        h.press(KeyType.Enter);
        assertFalse(h.connector.isVaultInitialized());
        assertInstanceOf(CreateScreen.class, h.manager.current());
    }

    @Test
    void mismatchIsNotShownWhileStillTypingAShorterConfirm() {
        h.type(GOOD);
        h.press(KeyType.Tab);
        h.type("Corr");
        assertFalse(h.text().contains("passwords do not match"));
    }

    @Test
    void weakPasswordIsRejectedWithReason() {
        h.type("short1A");
        h.press(KeyType.Enter);
        h.type("short1A");
        h.press(KeyType.Enter);

        assertTrue(h.text().contains("err: needs 12+ chars with upper, lower and a digit"), h.text());
        assertFalse(h.connector.isVaultInitialized());
    }

    @Test
    void validPasswordCreatesVaultAndOpensVaultView() {
        h.type(GOOD);
        h.press(KeyType.Enter);  // moves to confirm
        h.type(GOOD);
        h.press(KeyType.Enter);

        assertTrue(h.connector.isVaultInitialized());
        assertTrue(h.connector.isUnlocked());
        assertInstanceOf(VaultScreen.class, h.manager.current());
        assertTrue(h.text().contains("[UNLOCKED]"));
    }

    @Test
    void showsWorkingIndicatorWhileKdfRunsAndIgnoresInput(@TempDir Path other) {
        TuiHarness slow = new TuiHarness(other.resolve("vault.db"), false, List.of());
        try {
            slow.type(GOOD);
            slow.press(KeyType.Tab);
            slow.type(GOOD);
            slow.press(KeyType.Enter);

            assertEquals(1, slow.queuedJobs());
            assertTrue(slow.text().contains("creating "), slow.text());
            assertTrue(slow.text().contains("deriving key"));
            slow.type("xyz");
            assertFalse(slow.text().contains("*"), "fields were taken and input is disabled");

            slow.runWorker();
            assertInstanceOf(VaultScreen.class, slow.manager.current());
        } finally {
            slow.close();
        }
    }

    @Test
    void openWarningIsShown(@TempDir Path other) {
        TuiHarness warned = new TuiHarness(other.resolve("vault.db"), true,
                List.of("Vault file /x/vault.db has permissions rw-r--r--"));
        try {
            assertTrue(warned.text().contains("err: Vault file /x/vault.db has permissions rw-r--r--"));
        } finally {
            warned.close();
        }
    }

    @Test
    void escQuits() {
        h.press(KeyType.Escape);
        assertTrue(h.manager.isQuitRequested());
    }
}
