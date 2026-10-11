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

class RevealCopyTest {

    private static final String MASTER = "Correct9Horse";

    @TempDir
    Path tmp;
    private TuiHarness h;
    private VaultScreen v;

    private TuiHarness start(boolean withClipboard) {
        TuiHarness harness = new TuiHarness(tmp.resolve(withClipboard ? "vault.db" : "headless.db"), true, List.of(), withClipboard);
        harness.connector.createVault(MASTER.toCharArray());
        harness.connector.addEntry("alpha.com", "alice", "Alpha1Secret".toCharArray(), "line one\nline two");
        harness.connector.addEntry("beta.com", "bob", "Beta2Secret".toCharArray(), null);
        v = new VaultScreen(harness.ctx);
        harness.manager.show(v);
        harness.render();
        return harness;
    }

    @BeforeEach
    void setUp() {
        h = start(true);
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    // --- reveal ---

    @Test
    void revealShowsPasswordWithCountdownThenReMasks() {
        assertFalse(h.text().contains("Alpha1Secret"));
        h.type("s");
        assertTrue(h.text().contains("password: Alpha1Secret  (hides in 10s)"), h.text());

        h.advance(3_000);
        assertTrue(h.text().contains("(hides in 7s)"));

        char[] shown = v.shownPassword();
        h.advance(7_000);
        assertFalse(h.text().contains("Alpha1Secret"));
        assertTrue(h.text().contains("password: ************"));
        assertArrayEquals(new char["Alpha1Secret".length()], shown, "wiped on re-mask");
    }

    @Test
    void sAgainHidesImmediately() {
        h.type("s");
        h.type("s");
        assertFalse(h.text().contains("Alpha1Secret"));
        assertNull(v.shownPassword());
    }

    @Test
    void movingSelectionReMasks() {
        h.type("s");
        char[] shown = v.shownPassword();
        h.type("j");
        assertFalse(h.text().contains("Alpha1Secret"));
        assertFalse(h.text().contains("Beta2Secret"), "the new selection is not revealed either");
        assertArrayEquals(new char[shown.length], shown);
    }

    @Test
    void searchReMasks() {
        h.type("s");
        h.type("/");
        assertFalse(h.text().contains("Alpha1Secret"));
    }

    @Test
    void helpOverlayReMasks() {
        h.type("s");
        h.type("?");
        assertFalse(h.text().contains("Alpha1Secret"));
        h.type("x");
        assertFalse(h.text().contains("Alpha1Secret"), "not shown again after closing help");
    }

    @Test
    void lockAndAutoLockReMask() {
        h.type("s");
        char[] shown = v.shownPassword();
        h.type("l");
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertArrayEquals(new char[shown.length], shown);

        h.connector.unlockVault(MASTER.toCharArray());
        v = new VaultScreen(h.ctx);
        h.manager.show(v);
        h.type("s");
        shown = v.shownPassword();
        h.connector.lockVault();
        h.tick();
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertArrayEquals(new char[shown.length], shown);
    }

    // --- notes ---

    @Test
    void notesShowOnDemandAndReMask() {
        assertTrue(h.text().contains("notes:    (hidden)"));
        assertFalse(h.text().contains("line one"));
        h.type("n");
        assertTrue(h.text().contains("notes:    line one line two  (hides in 10s)"), h.text());
        h.advance(10_000);
        assertTrue(h.text().contains("notes:    (hidden)"));
        assertFalse(h.text().contains("line one"));
    }

    @Test
    void entryWithoutNotesShowsNone() {
        h.type("j");
        h.type("n");
        assertTrue(h.text().contains("notes:    (none)  (hides in 10s)"), h.text());
    }

    // --- copy ---

    @Test
    void copyPutsPasswordOnClipboardAndClearsAfter20s() {
        h.type("c");
        assertEquals("Alpha1Secret", h.clipboard.text);
        assertFalse(h.text().contains("Alpha1Secret"), "copy does not reveal");
        assertTrue(h.text().contains("ok: copied (20s)"), h.text());

        h.advance(2_000);
        assertTrue(h.text().contains("ok: copied (18s)"));

        h.advance(18_000);
        assertEquals("", h.clipboard.text);
        assertTrue(h.text().contains("ok: clipboard cleared"));
    }

    @Test
    void clipboardIsNotClearedIfUserCopiedSomethingElse() {
        h.type("c");
        h.clipboard.text = "something the user copied";
        h.advance(20_000);
        assertEquals("something the user copied", h.clipboard.text);
    }

    @Test
    void lockAndQuitClearACopiedPasswordImmediately() {
        h.type("c");
        h.type("l");
        assertEquals("", h.clipboard.text);

        h.connector.unlockVault(MASTER.toCharArray());
        h.manager.show(new VaultScreen(h.ctx));
        h.type("c");
        h.type("q");
        assertTrue(h.manager.isQuitRequested());
        assertEquals("", h.clipboard.text);
    }

    @Test
    void copyUsernameHasNoAutoClear() {
        h.type("u");
        assertEquals("alice", h.clipboard.text);
        assertTrue(h.text().contains("ok: username copied"));
        h.advance(60_000);
        assertEquals("alice", h.clipboard.text);
    }

    @Test
    void copyingUsernameAfterPasswordCancelsThePasswordClear() {
        h.type("c");
        h.type("u");
        h.advance(20_000);
        assertEquals("alice", h.clipboard.text, "username stays; the password is already gone");
    }

    // --- no clipboard (headless / SSH) ---

    @Test
    void headlessFallsBackToReveal() {
        h.close();
        h = start(false);
        h.type("c");
        assertTrue(h.text().contains("err: clipboard unavailable - press [s] to reveal"), h.text());
        h.type("u");
        assertTrue(h.text().contains("err: clipboard unavailable - press [s] to reveal"));
        h.type("s");
        assertTrue(h.text().contains("password: Alpha1Secret"), "reveal works everywhere");
    }

    @Test
    void helpNoLongerMarksTheseKeysSoon() {
        h.type("?");
        String s = h.text();
        assertTrue(s.contains("reveal password 10s") && !s.contains("reveal password 10s (soon)"));
        assertTrue(s.contains("copy password (20s clear)") && !s.contains("copy password (20s clear) (soon)"));
        assertTrue(s.contains("copy username") && !s.contains("copy username (soon)"));
        assertTrue(s.contains("show notes") && !s.contains("show notes (soon)"));
        h.type("x");
        assertTrue(h.text().contains("[s] reveal  [c] copy  [u] copy user"));
        assertTrue(h.text().contains("[n] show notes"));
    }
}
