package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.TuiHarness;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    private static final String MASTER = "Correct9Horse";

    @TempDir
    Path tmp;
    private TuiHarness h;

    @BeforeEach
    void setUp() {
        h = new TuiHarness(tmp.resolve("vault.db"));
        h.connector.createVault(MASTER.toCharArray());
        h.connector.addEntry("github.com", "mave", "Hunter2Pass".toCharArray(), null);
        h.manager.show(new VaultScreen(h.ctx));
        h.render();
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    private String header() {
        return h.text().split("\n")[0];
    }

    @Test
    void headerShowsIdleCountdown() {
        assertTrue(header().contains("[UNLOCKED] | auto-lock 5:00"), header());
        h.advance(28_000);
        assertTrue(header().contains("auto-lock 4:32"), header());
    }

    @Test
    void anyKeypressResetsTheTimer() {
        h.advance(200_000);
        assertTrue(header().contains("auto-lock 1:40"), header());
        h.type("j");
        assertTrue(header().contains("auto-lock 5:00"), header());
        h.advance(200_000);
        h.press(KeyType.ArrowUp);
        assertTrue(header().contains("auto-lock 5:00"));
    }

    @Test
    void lastThirtySecondsShowLockingInErrorColor() {
        h.advance(269_000);
        assertTrue(header().contains("[UNLOCKED] | auto-lock 0:31"), header());
        h.advance(1_000);
        assertTrue(header().contains("[LOCKING] | auto-lock 0:30"), header());
        int col = header().indexOf("[LOCKING]");
        assertEquals(TuiHarness.THEME.error, h.terminal.getBufferCharacter(col, 0).getForegroundColor());
    }

    @Test
    void idleTimeoutLocksAndReturnsToUnlock() {
        h.type("c");  // our password on the clipboard
        h.advance(299_999);
        assertTrue(h.connector.isUnlocked());
        h.advance(1);
        assertFalse(h.connector.isUnlocked());
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: auto-locked after 5 min idle"), h.text());
        assertEquals("", h.clipboard.text, "our copied password is cleared");
        assertFalse(h.text().contains("github.com"), "no vault data left on screen");
    }

    @Test
    void lockKeyReturnsWithLockedNotice() {
        h.type("l");
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: vault locked"));
    }

    @Test
    void typingInAModalKeepsTheSessionAlive() {
        h.type("a");
        h.advance(250_000);
        h.type("x");
        h.advance(250_000);
        assertTrue(h.connector.isUnlocked(), "keypresses in the form reset the timer");
        assertInstanceOf(EntryModal.class, h.manager.current());
        assertTrue(header().contains("auto-lock 0:50"), header());
    }

    @Test
    void idleTimeoutWithAModalOpenClosesAndWipesIt() {
        h.type("a");
        h.type("site");
        EntryModal m = (EntryModal) h.manager.current();
        h.press(KeyType.Tab);
        h.press(KeyType.Tab);
        h.type("secret");
        h.advance(300_000);
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertEquals(0, m.passwordField().length());
    }

    @Test
    void timerDoesNotRunWhileLocked() {
        h.type("l");
        h.advance(600_000);
        assertEquals(0, h.connector.getSessionRemainingMs());
        h.type(MASTER);
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("auto-lock 5:00"), "fresh session after unlock");
    }
}
