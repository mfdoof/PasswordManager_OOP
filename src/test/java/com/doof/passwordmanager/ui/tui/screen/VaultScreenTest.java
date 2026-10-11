package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.TuiHarness;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VaultScreenTest {

    private static final String MASTER = "Correct9Horse";

    @TempDir
    Path tmp;
    private TuiHarness h;

    @BeforeEach
    void setUp() {
        h = new TuiHarness(tmp.resolve("vault.db"));
        h.connector.createVault(MASTER.toCharArray());
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    private void add(String site, String user) {
        h.connector.addEntry(site, user, "Hunter2Pass".toCharArray(), "secret note");
    }

    private VaultScreen open() {
        VaultScreen v = new VaultScreen(h.ctx);
        h.manager.show(v);
        h.render();
        return v;
    }

    private VaultScreen screen() {
        return (VaultScreen) h.manager.current();
    }

    private String line(int row) {
        return h.text().split("\n")[row];
    }

    // --- layout and states ---

    @Test
    void headerFooterAndColumns() {
        add("github.com", "mave");
        open();
        String s = h.text();
        assertTrue(line(0).contains(" doof vault ") && line(0).contains("[UNLOCKED]"), line(0));
        assertTrue(s.contains("SITE") && s.contains("USERNAME") && s.contains("UPDATED"));
        assertTrue(s.contains("[j/k]move [/]search [l]ock [?]help [q]uit"));
        assertTrue(s.contains("1 entry"));
        assertTrue(s.contains(" details "));
    }

    @Test
    void emptyVaultState() {
        open();
        assertTrue(h.text().contains("vault is empty - press [a] to add an entry"), h.text());
        assertTrue(h.text().contains("0 entries"));
    }

    @Test
    void listIsSortedCaseInsensitivelyBySite() {
        add("zeta.io", "z");
        add("Alpha.com", "a");
        add("beta.org", "b");
        open();
        String s = h.text();
        int alpha = s.indexOf("Alpha.com"), beta = s.indexOf("beta.org"), zeta = s.indexOf("zeta.io");
        assertTrue(alpha < beta && beta < zeta, s);
        assertTrue(line(4).startsWith("│ > Alpha.com"), "first row selected: " + line(4));
    }

    @Test
    void updatedColumnShowsDate() {
        add("github.com", "mave");
        open();
        assertTrue(line(4).contains("2023-11-14"), line(4));  // harness clock 1_700_000_000_000 = 2023-11-14 UTC
    }

    // --- details ---

    @Test
    void detailsShowSiteUserMaskedPasswordAndHiddenNotes() {
        add("github.com", "mave@doof.quest");
        open();
        String s = h.text();
        assertTrue(s.contains("site:     github.com"), s);
        assertTrue(s.contains("user:     mave@doof.quest"));
        assertTrue(s.contains("password: ************"));
        assertTrue(s.contains("notes:    (hidden)"));
        assertFalse(s.contains("Hunter2Pass"), "password never shown");
        assertFalse(s.contains("secret note"), "notes never shown");
    }

    // --- navigation ---

    @Test
    void jkArrowsGgAndGMoveTheSelection() {
        add("a.com", "u");
        add("b.com", "u");
        add("c.com", "u");
        VaultScreen v = open();

        h.type("j");
        assertEquals("b.com", v.selectedEntry().getSite());
        h.press(KeyType.ArrowDown);
        assertEquals("c.com", v.selectedEntry().getSite());
        h.type("j");
        assertEquals("c.com", v.selectedEntry().getSite(), "stops at the end");
        h.type("k");
        h.press(KeyType.ArrowUp);
        assertEquals("a.com", v.selectedEntry().getSite());
        h.type("G");
        assertEquals("c.com", v.selectedEntry().getSite());
        h.type("g");
        assertEquals("c.com", v.selectedEntry().getSite(), "single g does nothing");
        h.type("g");
        assertEquals("a.com", v.selectedEntry().getSite());
        assertTrue(h.text().contains("site:     a.com"), "details follow the selection");
    }

    @Test
    void gThenOtherKeyCancelsGg() {
        add("a.com", "u");
        add("b.com", "u");
        VaultScreen v = open();
        h.type("G");
        h.type("g");
        h.type("k");
        h.type("g");
        assertEquals("a.com", v.selectedEntry().getSite(), "k moved up; the lone g after it waits");
    }

    @Test
    void listScrollsToKeepSelectionVisible() {
        for (int i = 0; i < 30; i++) add(String.format("site%02d.com", i), "u");
        VaultScreen v = open();
        h.type("G");
        assertEquals("site29.com", v.selectedEntry().getSite());
        assertTrue(h.text().contains("> site29.com"), h.text());
        assertFalse(h.text().contains("site00.com"), "top scrolled out of view");
        h.type("g");
        h.type("g");
        assertTrue(h.text().contains("> site00.com"));
    }

    @Test
    void longValuesAreTruncatedWithEllipsis() {
        String longSite = "a-very-long-subdomain.with-many-parts.example-company.com";
        add(longSite, "user");
        open();
        assertFalse(line(4).contains(longSite), "not shown in full in the list: " + line(4));
        assertTrue(line(4).contains("a-very-long-subdomain.wi..."), line(4));
        assertTrue(h.text().contains("site:     " + longSite), "details pane has room for it");
    }

    // --- search ---

    @Test
    void liveSearchOverSiteAndUserWithCount() {
        add("github.com", "mave");
        add("gitlab.com", "mave");
        add("example.org", "git-admin");
        add("other.net", "bob");
        VaultScreen v = open();

        h.type("/");
        assertTrue(v.isSearching());
        h.type("git");
        String s = h.text();
        assertTrue(s.contains("/ search: git_"), s);
        assertTrue(s.contains("3 of 4"));
        assertTrue(s.contains("example.org"), "matches on username too");
        assertFalse(s.contains("other.net"));

        h.press(KeyType.Backspace);
        h.press(KeyType.Backspace);
        h.press(KeyType.Backspace);
        assertTrue(h.text().contains("4 entries"), "empty query shows the total again");
    }

    @Test
    void escClearsSearchAndEnterKeepsFilter() {
        add("github.com", "mave");
        add("other.net", "bob");
        VaultScreen v = open();

        h.type("/hub");
        h.press(KeyType.Enter);
        assertFalse(v.isSearching());
        assertTrue(h.text().contains("1 of 2"));
        h.type("j");  // j navigates again, not typed into the query
        assertTrue(h.text().contains("/ search: hub"));

        h.press(KeyType.Escape);
        assertTrue(h.text().contains("2 entries"));

        h.type("/xyz");
        h.press(KeyType.Escape);
        assertFalse(v.isSearching());
        assertTrue(h.text().contains("2 entries"));
    }

    @Test
    void noMatchState() {
        add("github.com", "mave");
        open();
        h.type("/zzz");
        assertTrue(h.text().contains("no matches for \"zzz\" - [ESC] clear"), h.text());
        assertTrue(h.text().contains("0 of 1"));
    }

    @Test
    void selectionIsKeptWhenFilterStillContainsIt() {
        add("a.com", "u");
        add("b.com", "u");
        add("c.com", "u");
        VaultScreen v = open();
        h.type("jj");
        h.type("/.com");
        assertEquals("c.com", v.selectedEntry().getSite());
    }

    // --- status bar ---

    @Test
    void okNoticeAutoDismissesAfterFiveSeconds() {
        h.manager.show(new VaultScreen(h.ctx, "ok: vault unlocked"));
        h.render();
        assertTrue(h.text().contains("ok: vault unlocked"));
        h.advance(4_999);
        assertTrue(h.text().contains("ok: vault unlocked"));
        h.advance(1);
        assertFalse(h.text().contains("ok: vault unlocked"));
    }

    @Test
    void notYetBuiltKeysShowAnErrorUntilTheNextKey() {
        open();
        h.type("a");
        assertTrue(h.text().contains("err: [a] add is not available yet"), h.text());
        h.advance(60_000);
        assertTrue(h.text().contains("err: [a] add is not available yet"), "errors do not time out");
        h.type("j");
        assertFalse(h.text().contains("err:"));
    }

    // --- help, lock, quit ---

    @Test
    void helpOverlayListsAllKeysAndAnyKeyCloses() {
        add("a.com", "u");
        add("b.com", "u");
        VaultScreen v = open();
        h.type("j");
        h.type("?");
        assertInstanceOf(HelpOverlay.class, h.manager.current());
        String s = h.text();
        for (HelpOverlay.KeyHelp k : HelpOverlay.KEYS) {
            assertTrue(s.contains(k.keys()), k.keys());
            assertTrue(s.contains(k.action() + (k.built() ? "" : " (soon)")), k.action());
        }
        assertTrue(s.contains("any key closes"));

        h.type("x");
        assertSame(v, h.manager.current());
        assertEquals("b.com", v.selectedEntry().getSite(), "state kept");
    }

    @Test
    void lockReturnsToUnlockAndQuitLocks() {
        open();
        h.type("l");
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertFalse(h.connector.isUnlocked());

        h.connector.unlockVault(MASTER.toCharArray());
        open();
        h.type("q");
        assertTrue(h.manager.isQuitRequested());
        assertFalse(h.connector.isUnlocked());
    }

    @Test
    void autoLockReturnsToUnlockEvenWithHelpOpen() {
        open();
        h.type("?");
        h.connector.lockVault();
        h.tick();
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: vault auto-locked"));
    }

    @Test
    void layoutReflowsOnLargerTerminal() {
        for (int i = 0; i < 30; i++) add(String.format("site%02d.com", i), "u");
        open();
        h.resize(120, 40);
        String s = h.text();
        assertTrue(s.contains("site20.com"), "more rows visible");
        assertTrue(s.split("\n")[0].length() == 120);
    }
}
