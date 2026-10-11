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

class UnlockScreenTest {

    private static final String RIGHT = "Correct9Horse";
    private static final String WRONG = "Wrong9Horse!!";

    @TempDir
    Path tmp;
    private Path vault;
    private TuiHarness h;

    @BeforeEach
    void setUp() {
        vault = tmp.resolve("vault.db");
        TuiHarness setup = new TuiHarness(vault);
        setup.connector.createVault(RIGHT.toCharArray());
        setup.connector.lockVault();
        setup.close();
        h = new TuiHarness(vault);
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    @Test
    void existingVaultOpensUnlockScreen() {
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        String s = h.text();
        assertTrue(s.contains("unlock"));
        assertTrue(s.contains("> master password: _"), s);
        assertTrue(s.contains("[ENTER] unlock    [ESC] quit"));
        assertTrue(s.contains("vault: ~/.doof/vault.db"));
        assertTrue(s.contains("######   #####   #####  #######"), "logo");
    }

    @Test
    void inputIsMasked() {
        h.type(RIGHT);
        assertTrue(h.text().contains("> master password: *************_"));
        assertFalse(h.text().contains(RIGHT));
    }

    @Test
    void rightPasswordUnlocksAndOpensVaultView() {
        h.type(RIGHT);
        h.press(KeyType.Enter);
        assertTrue(h.connector.isUnlocked());
        assertInstanceOf(VaultPlaceholderScreen.class, h.manager.current());
    }

    @Test
    void emptySubmitAsksForPassword() {
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: enter your master password"));
        assertFalse(h.connector.isUnlocked());
    }

    @Test
    void wrongPasswordShowsAttemptAndCountdownAndDisablesInput() {
        h.type(WRONG);
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: invalid password (attempt 1) - wait 1s"), h.text());
        assertFalse(h.text().contains("*"), "field cleared after submit");

        h.type("abc");  // refused during the wait
        assertFalse(h.text().contains("***"));
        assertFalse(h.text().contains("> master password: _"), "no cursor while disabled");

        h.advance(1_000);
        assertTrue(h.text().contains("err: invalid password (attempt 1)"));
        assertFalse(h.text().contains("wait"));

        h.type(WRONG);
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: invalid password (attempt 2) - wait 2s"), h.text());
        h.advance(1_000);
        assertTrue(h.text().contains("- wait 1s"), "countdown ticks down");
        h.advance(1_000);

        h.type(RIGHT);
        h.press(KeyType.Enter);
        assertTrue(h.connector.isUnlocked());
    }

    @Test
    void throttleStateIsShownOnStartup() {
        h.type(WRONG);
        h.press(KeyType.Enter);
        h.advance(1_000);
        h.type(WRONG);
        h.press(KeyType.Enter);
        h.close();

        TuiHarness restarted = new TuiHarness(vault);  // same file, fresh UI ("restart")
        try {
            // The restarted harness has its own clock at the same start time, so the full 2s wait remains.
            restarted.clock.advance(h.clock.millis() - restarted.clock.millis());
            restarted.manager.show(new UnlockScreen(restarted.ctx));
            restarted.tick();
            assertTrue(restarted.text().contains("err: 2 failed attempts - wait 2s"), restarted.text());
            restarted.type("abc");
            assertFalse(restarted.text().contains("***"), "input disabled during the wait");
        } finally {
            restarted.close();
            h = new TuiHarness(vault);  // for tearDown
        }
    }

    @Test
    void workingIndicatorWhileKdfRuns(@TempDir Path other) {
        TuiHarness slow = new TuiHarness(vault, false, List.of());
        try {
            slow.type(RIGHT);
            slow.press(KeyType.Enter);
            assertTrue(slow.text().contains("unlocking "), slow.text());
            assertTrue(((UnlockScreen) slow.manager.current()).isWorking());

            slow.type("zzz");
            assertFalse(slow.text().contains("***"), "input ignored while working");

            slow.runWorker();
            assertInstanceOf(VaultPlaceholderScreen.class, slow.manager.current());
        } finally {
            slow.close();
        }
    }

    @Test
    void lockReturnsToUnlockScreen() {
        h.type(RIGHT);
        h.press(KeyType.Enter);
        h.type("l");
        assertFalse(h.connector.isUnlocked());
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: vault locked"));

        h.type("x");
        assertFalse(h.text().contains("ok: vault locked"), "notice clears on the next key");
    }

    @Test
    void autoLockIsDetectedAndReturnsToUnlock() {
        h.type(RIGHT);
        h.press(KeyType.Enter);
        h.connector.lockVault();  // what VaultService's auto-lock timer does
        h.tick();
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: vault auto-locked"));
    }

    @Test
    void qOnVaultViewLocksAndQuits() {
        h.type(RIGHT);
        h.press(KeyType.Enter);
        h.type("q");
        assertTrue(h.manager.isQuitRequested());
        assertFalse(h.connector.isUnlocked());
    }

    @Test
    void qIsAPasswordCharacterOnUnlockAndEscQuits() {
        h.type("q");
        assertFalse(h.manager.isQuitRequested());
        assertTrue(h.text().contains("> master password: *_"));
        h.press(KeyType.Escape);
        assertTrue(h.manager.isQuitRequested());
    }
}
