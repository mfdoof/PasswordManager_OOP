package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.service.EntryService.EntrySummary;
import com.doof.passwordmanager.ui.tui.TuiHarness;
import com.doof.passwordmanager.ui.tui.widget.PasswordGenerator;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EntryModalTest {

    private static final String MASTER = "Correct9Horse";

    @TempDir
    Path tmp;
    private TuiHarness h;
    private VaultScreen v;

    @BeforeEach
    void setUp() {
        h = new TuiHarness(tmp.resolve("vault.db"));
        h.connector.createVault(MASTER.toCharArray());
        v = new VaultScreen(h.ctx);
        h.manager.show(v);
        h.render();
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    private EntryModal modal() {
        return (EntryModal) h.manager.current();
    }

    private void tab(int n) {
        for (int i = 0; i < n; i++) h.press(KeyType.Tab);
    }

    private void ctrl(char c) {
        h.key(new KeyStroke(c, true, false));
    }

    private String notes(long id) {
        return h.connector.withNotes(id, n -> n == null ? null : new String(n));
    }

    private String password(long id) {
        return h.connector.withPassword(id, String::new);
    }

    /** Opens add, fills site/user/password(/notes), returns without saving. */
    private void fill(String site, String user, String pw, String notes) {
        h.type("a");
        h.type(site);
        tab(1);
        h.type(user);
        tab(1);
        h.type(pw);
        if (notes != null) {
            tab(1);
            h.type(notes);
        }
    }

    // --- add ---

    @Test
    void emptyVaultHintWorksAndAddOpensForm() {
        assertTrue(h.text().contains("vault is empty - press [a] to add an entry"));
        h.type("a");
        assertInstanceOf(EntryModal.class, h.manager.current());
        String s = h.text();
        assertTrue(s.contains(" add entry "), s);
        assertTrue(s.contains("site     > _"));
        assertTrue(s.contains("[TAB] next  [ENTER] save  [ESC] cancel"));
        assertTrue(s.contains("length: 20  [x] A-Z [x] a-z [x] 0-9 [x] !@#"), s);
    }

    @Test
    void addSavesRefreshesSelectsAndReports() {
        h.connector.addEntry("aaa.com", "first", "x".toCharArray(), null);
        v.reload();
        fill("github.com", "mave", "s3cret-PW", "my note");
        h.press(KeyType.Enter);

        assertSame(v, h.manager.current());
        EntrySummary sel = v.selectedEntry();
        assertEquals("github.com", sel.getSite(), "selection on the new entry");
        assertEquals("s3cret-PW", password(sel.getId()));
        assertEquals("my note", notes(sel.getId()));
        assertTrue(h.text().contains("ok: added github.com"), h.text());
        assertTrue(h.text().contains("2 entries"));
    }

    @Test
    void passwordIsMaskedAndVToggles() {
        fill("github.com", "mave", "s3cret", null);
        assertTrue(h.text().contains("password > ******_"), h.text());
        assertFalse(h.text().contains("s3cret"));

        ctrl('v');
        assertTrue(h.text().contains("password > s3cret_"));
        assertTrue(h.text().contains("[v] hide"));
        ctrl('v');
        assertFalse(h.text().contains("s3cret"));

        tab(2);  // notes -> generator row, where plain v toggles
        h.type("v");
        assertTrue(h.text().contains("s3cret"));
    }

    @Test
    void vAndGAreOrdinaryCharactersInFields() {
        fill("vg.com", "vg", "gvgv", "gv");
        h.press(KeyType.Enter);
        EntrySummary e = v.selectedEntry();
        assertEquals("vg.com", e.getSite());
        assertEquals("gvgv", password(e.getId()));
    }

    @Test
    void strengthBarIsLive() {
        h.type("a");
        tab(2);
        assertTrue(h.text().contains("strength [----------] none"));
        h.type("abc");
        assertTrue(h.text().contains("weak"));
        h.type("DEF123!@#xyzQRS");
        assertTrue(h.text().contains("strong"), h.text());
    }

    @Test
    void tabAndShiftTabMoveBetweenFields() {
        h.type("a");
        assertEquals(EntryModal.Field.SITE, modal().focus());
        tab(4);
        assertEquals(EntryModal.Field.GENERATOR, modal().focus());
        tab(1);
        assertEquals(EntryModal.Field.SITE, modal().focus(), "wraps around");
        h.press(KeyType.ReverseTab);
        assertEquals(EntryModal.Field.GENERATOR, modal().focus());
        h.press(KeyType.ReverseTab);
        assertEquals(EntryModal.Field.NOTES, modal().focus());
    }

    // --- inline validation ---

    @Test
    void emptySiteAndUserAreRejectedInline() {
        h.type("a");
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: site is required"), h.text());
        assertEquals(EntryModal.Field.SITE, modal().focus());

        h.type("github.com");
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: user is required"));
        assertEquals(EntryModal.Field.USER, modal().focus());

        h.type("mave");
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: password is required"));
        assertTrue(h.connector.listEntries().isEmpty());
    }

    @Test
    void duplicateIsRejectedInlineBeforeSaving() {
        h.connector.addEntry("github.com", "mave", "x".toCharArray(), null);
        fill("GitHub.com", "MAVE", "other", null);
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: this site + user already exists"), h.text());
        assertEquals(1, h.connector.listEntries().size());
        assertInstanceOf(EntryModal.class, h.manager.current(), "form stays open with the input");
        assertEquals(5, modal().passwordField().length(), "a failed save keeps what was typed");
    }

    @Test
    void serviceValidationErrorsAreShownInline() {
        fill("example.com:abc", "mave", "pw", null);  // bad port: rejected by InputValidator.validateWebsite
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: invalid website format"), h.text());
    }

    // --- generator ---

    @Test
    void generatorFillsPasswordWithDefaultLengthAndAllSets() {
        h.type("a");
        tab(4);
        h.type("g");
        char[] pw = modal().passwordField().peek();
        assertEquals(20, pw.length);
        String s = new String(pw);
        assertTrue(s.chars().anyMatch(Character::isUpperCase) && s.chars().anyMatch(Character::isLowerCase)
                && s.chars().anyMatch(Character::isDigit), s);
        assertTrue(s.chars().anyMatch(c -> PasswordGenerator.SYMBOLS.indexOf(c) >= 0), s);
        assertTrue(h.text().contains("password > ********"), "generated password is masked");
    }

    @Test
    void ctrlGGeneratesFromAnyField() {
        h.type("a");
        ctrl('g');
        assertEquals(20, modal().passwordField().length());
    }

    @Test
    void lengthAdjustsWithPlusMinusAndTypingWithinRange() {
        h.type("a");
        tab(4);
        h.type("+");
        assertTrue(h.text().contains("length: 21"));
        h.type("--");
        assertTrue(h.text().contains("length: 19"));
        h.type("32");
        assertTrue(h.text().contains("length: 32"), h.text());
        h.type("g");
        assertEquals(32, modal().passwordField().length());

        h.press(KeyType.Backspace);
        h.press(KeyType.Backspace);
        h.type("5");
        h.type("g");
        assertTrue(h.text().contains("err: length must be 8-128"), h.text());

        h.type("--");  // from an invalid value +/- snap to the range
        h.type("1290");
        h.type("+");
        assertTrue(h.text().contains("length: 128"), h.text());
    }

    @Test
    void charsetTogglesApplyAndAllOffIsRefused() {
        h.type("a");
        tab(4);
        h.press(KeyType.ArrowRight);   // A-Z
        h.type(" ");
        h.press(KeyType.ArrowRight);   // a-z
        h.press(KeyType.ArrowRight);   // 0-9
        h.press(KeyType.ArrowRight);   // !@#
        h.type(" ");
        assertTrue(h.text().contains("[ ] A-Z [x] a-z [x] 0-9 [ ] !@#"), h.text());
        h.type("g");
        String pw = new String(modal().passwordField().peek());
        assertTrue(pw.chars().allMatch(c -> Character.isLowerCase(c) || Character.isDigit(c)), pw);

        h.press(KeyType.ArrowLeft);    // 0-9
        h.type(" ");
        h.press(KeyType.ArrowLeft);    // a-z
        h.type(" ");
        h.type("g");
        assertTrue(h.text().contains("err: enable at least one character set"));
    }

    // --- cancel / discard ---

    @Test
    void escWithoutChangesClosesRightAway() {
        h.type("a");
        h.press(KeyType.Escape);
        assertSame(v, h.manager.current());
    }

    @Test
    void escWithChangesAsksAndDefaultsToNo() {
        fill("github.com", "mave", "s3cret", null);
        EntryModal m = modal();
        h.press(KeyType.Escape);
        assertTrue(h.text().contains("discard changes? [y/N]"));
        h.press(KeyType.Enter);  // not y: stays
        assertSame(m, h.manager.current());
        assertFalse(h.text().contains("discard changes?"));

        h.press(KeyType.Escape);
        h.type("y");
        assertSame(v, h.manager.current());
        assertTrue(h.connector.listEntries().isEmpty());
        assertEquals(0, m.passwordField().length(), "wiped on cancel");
    }

    // --- edit ---

    @Test
    void editIsPrefilledAndEmptyPasswordAndNotesKeepCurrent() {
        long id = h.connector.addEntry("github.com", "mave", "Orig1nal!".toCharArray(), "keep me");
        v.reload();
        h.render();
        h.type("e");
        String s = h.text();
        assertTrue(s.contains(" edit entry "));
        assertTrue(s.contains("site     > github.com_"), s);
        assertTrue(s.contains("user     > mave"));
        assertTrue(s.contains("(keep current)"));

        tab(1);
        h.type("2");  // user -> mave2
        h.press(KeyType.Enter);

        assertTrue(h.text().contains("ok: saved github.com"), h.text());
        assertEquals("mave2", h.connector.listEntries().stream().filter(e -> e.getId() == id).findFirst().orElseThrow().getUser());
        assertEquals("Orig1nal!", password(id));
        assertEquals("keep me", notes(id));
        assertEquals(id, v.selectedEntry().getId());
    }

    @Test
    void editWithNewPasswordReplacesIt() {
        long id = h.connector.addEntry("github.com", "mave", "Orig1nal!".toCharArray(), null);
        v.reload();
        h.type("e");
        tab(2);
        h.type("Brand-new-2");
        h.press(KeyType.Enter);
        assertEquals("Brand-new-2", password(id));
    }

    @Test
    void savingWhileFilteredSelectsTheEntryEvenIfFilterWouldHideIt() {
        h.connector.addEntry("github.com", "mave", "x".toCharArray(), null);
        v.reload();
        h.type("/git");
        h.press(KeyType.Enter);
        fill("other.org", "bob", "pw", null);
        h.press(KeyType.Enter);
        assertEquals("other.org", v.selectedEntry().getSite());
    }

    // --- delete ---

    @Test
    void deleteAsksAndDefaultsToNo() {
        h.connector.addEntry("github.com", "mave@doof.quest", "x".toCharArray(), null);
        v.reload();
        h.type("d");
        assertTrue(h.text().contains("err: delete github.com (mave@doof.quest)? [y/N]"), h.text());
        h.press(KeyType.Enter);
        assertEquals(1, h.connector.listEntries().size());
        h.type("d");
        h.type("n");
        assertEquals(1, h.connector.listEntries().size());
        assertFalse(h.text().contains("delete github.com"));
    }

    @Test
    void deleteYesRemovesAndSelectsTheNeighbour() {
        h.connector.addEntry("a.com", "u", "x".toCharArray(), null);
        h.connector.addEntry("b.com", "u", "x".toCharArray(), null);
        h.connector.addEntry("c.com", "u", "x".toCharArray(), null);
        v.reload();
        h.type("j");
        h.type("d");
        h.type("y");
        assertEquals(2, h.connector.listEntries().size());
        assertTrue(h.text().contains("ok: deleted b.com"), h.text());
        assertEquals("c.com", v.selectedEntry().getSite());
    }

    // --- lock ---

    @Test
    void autoLockClosesTheFormAndWipesIt() {
        fill("github.com", "mave", "s3cret", "note");
        EntryModal m = modal();
        h.connector.lockVault();
        h.tick();
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: auto-locked after 5 min idle"));
        assertEquals(0, m.passwordField().length());
        assertEquals(0, m.notesField().length());
    }

    @Test
    void helpShowsTheseKeysAsBuilt() {
        h.type("?");
        String s = h.text();
        assertTrue(s.contains("add / edit / delete") && s.contains("generate password (entry form)"));
        assertTrue(s.contains("change master password"));
        assertFalse(s.contains("(soon)"));
    }
}
