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

class ChangePasswordModalTest {

    private static final String OLD = "Correct9Horse";
    private static final String NEW = "Battery7Staple";

    @TempDir
    Path tmp;
    private TuiHarness h;
    private long id;

    private TuiHarness start(boolean inlineWorker, String file) {
        TuiHarness harness = new TuiHarness(tmp.resolve(file), inlineWorker, List.of());
        harness.connector.createVault(OLD.toCharArray());
        id = harness.connector.addEntry("github.com", "mave", "Hunter2Pass".toCharArray(), "note");
        harness.connector.addEntry("gitlab.com", "mave", "Other3Pass".toCharArray(), null);
        harness.manager.show(new VaultScreen(harness.ctx));
        harness.render();
        return harness;
    }

    @BeforeEach
    void setUp() {
        h = start(true, "vault.db");
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    private ChangePasswordModal modal() {
        return (ChangePasswordModal) h.manager.current();
    }

    private void fill(String current, String next, String confirm) {
        h.type(current);
        h.press(KeyType.Tab);
        h.type(next);
        h.press(KeyType.Tab);
        h.type(confirm);
    }

    @Test
    void opensWithChecklistAndMaskedFields() {
        h.type("p");
        String s = h.text();
        assertTrue(s.contains(" change master password "), s);
        assertTrue(s.contains("current > _"));
        assertTrue(s.contains("[ ] 12+ chars  [ ] upper  [ ] lower"));
        assertTrue(s.contains("[TAB] next  [ENTER] change  [ESC] cancel"));
        fill(OLD, NEW, NEW);
        assertFalse(h.text().contains(OLD) || h.text().contains(NEW), "masked");
        assertTrue(h.text().contains("[x] 12+ chars  [x] upper  [x] lower"), h.text());
    }

    @Test
    void successRotatesAndKeepsSessionAndEntries() {
        h.type("p");
        fill(OLD, NEW, NEW);
        h.press(KeyType.Enter);

        assertInstanceOf(VaultScreen.class, h.manager.current());
        assertTrue(h.text().contains("ok: master password changed"), h.text());
        assertTrue(h.connector.isUnlocked(), "session continues with the new key");
        assertEquals("Hunter2Pass", h.connector.withPassword(id, String::new));

        h.connector.lockVault();
        h.connector.unlockVault(NEW.toCharArray());
        assertEquals("note", h.connector.revealNotes(id));
    }

    @Test
    void weakAndMismatchedNewPasswordsAreRejectedInline() {
        h.type("p");
        fill(OLD, "short1A", "short1A");
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: needs 12+ chars, upper, lower, digit"), h.text());

        h.press(KeyType.Escape);
        h.type("p");
        fill(OLD, NEW, "Battery7Stapl3");
        assertTrue(h.text().contains("err: new passwords do not match"));
        h.press(KeyType.Enter);
        assertInstanceOf(ChangePasswordModal.class, h.manager.current());
    }

    @Test
    void wrongCurrentPasswordIsThrottledInline() {
        h.type("p");
        fill("Wrong9Horse!!", NEW, NEW);
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("err: wrong current password (attempt 1) - wait 1s"), h.text());
        assertEquals(0, modal().field(0).length(), "current password cleared");
        assertEquals(NEW.length(), modal().field(1).length(), "new password kept");

        h.type("abc");
        assertEquals(0, modal().field(0).length(), "input disabled during the wait");

        h.advance(1_000);
        h.type(OLD);
        h.press(KeyType.Tab);
        h.press(KeyType.Tab);
        h.press(KeyType.Enter);
        assertTrue(h.text().contains("ok: master password changed"), h.text());
    }

    @Test
    void showsReEncryptingSpinnerWhileWorking() {
        h.close();
        h = start(false, "slow.db");
        h.type("p");
        fill(OLD, NEW, NEW);
        h.press(KeyType.Enter);
        assertTrue(modal().isWorking());
        assertTrue(h.text().contains("re-encrypting 2 entries"), h.text());
        h.press(KeyType.Escape);
        assertInstanceOf(ChangePasswordModal.class, h.manager.current(), "cannot cancel mid-rotation");
        h.runWorker();
        assertTrue(h.text().contains("ok: master password changed"));
    }

    @Test
    void escCancelsAndWipes() {
        h.type("p");
        fill(OLD, NEW, NEW);
        ChangePasswordModal m = modal();
        h.press(KeyType.Escape);
        assertInstanceOf(VaultScreen.class, h.manager.current());
        for (int i = 0; i < 3; i++) assertEquals(0, m.field(i).length());
        h.connector.lockVault();
        h.connector.unlockVault(OLD.toCharArray());  // unchanged
    }

    @Test
    void autoLockClosesAndWipes() {
        h.type("p");
        fill(OLD, NEW, NEW);
        ChangePasswordModal m = modal();
        h.advance(300_000);
        assertInstanceOf(UnlockScreen.class, h.manager.current());
        for (int i = 0; i < 3; i++) assertEquals(0, m.field(i).length());
    }
}
