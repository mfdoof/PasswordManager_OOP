package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.CryptoException;
import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.dao.JdbcVaultRotationDao;
import com.doof.passwordmanager.dao.VaultRotationDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.model.VaultMetadata;
import com.doof.passwordmanager.testutil.MutableClock;
import com.doof.passwordmanager.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class VaultRotationTest {

    private static final String OLD = "OldMaster1";
    private static final String NEW = "NewMaster2";
    private static final String WRONG = "WrongPass3";
    private static final KdfParams UPGRADED = new KdfParams(2048, 2, 1);

    @TempDir
    Path tmp;

    private MutableClock clock;
    private ConnectionManager cm;
    private FaultyRotationDao rotationDao;
    private VaultService vault;
    private EntryService entries;
    private long a, b, c;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(1_700_000_000_000L);
        cm = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        rotationDao = new FaultyRotationDao(new JdbcVaultRotationDao(cm, clock));
        vault = new VaultService(new JdbcVaultMetadataDao(cm, clock), rotationDao, VaultServiceTest.FAST, clock);
        entries = new EntryService(new JdbcEntryDao(cm, clock), vault);

        vault.createVault(OLD.toCharArray());
        a = entries.addEntry("a.example.com", "alice", "Alice1Pass".toCharArray(), "note a");
        b = entries.addEntry("b.example.com", "bob", "Bob2Passw".toCharArray(), null);
        c = entries.addEntry("c.example.com", "carol", "Carol3Pass".toCharArray(), "note c");
    }

    /** Fresh services over the same file, like restarting the app (current defaults = {@code params}). */
    private EntryService restart(KdfParams params, String unlockWith) {
        VaultService v = new VaultService(new JdbcVaultMetadataDao(cm, clock), new JdbcVaultRotationDao(cm, clock), params, clock);
        EntryService e = new EntryService(new JdbcEntryDao(cm, clock), v);
        v.unlockVault(unlockWith.toCharArray());
        return e;
    }

    private void assertEntriesIntact(EntryService es) {
        assertEquals(List.of(a, b, c), es.listEntries().stream().map(EntryService.EntrySummary::getId).toList());
        assertEquals("alice", es.getEntry(a).getUser());
        assertEquals("Alice1Pass", es.withPassword(a, String::new));
        assertEquals("Bob2Passw", es.withPassword(b, String::new));
        assertEquals("Carol3Pass", es.withPassword(c, String::new));
        assertEquals("note a", es.revealNotes(a));
        assertNull(es.revealNotes(b));
    }

    private VaultMetadata metadata() {
        return new JdbcVaultMetadataDao(cm).find();
    }

    // --- success ---

    @Test
    void afterRotationNewPasswordUnlocksAndOldFails() {
        vault.rotateVault(OLD.toCharArray(), NEW.toCharArray());
        vault.lockVault();

        assertThrows(InvalidPasswordException.class, () -> vault.unlockVault(OLD.toCharArray()));
        clock.advance(1_000);
        vault.unlockVault(NEW.toCharArray());
        assertTrue(vault.isUnlocked());
    }

    @Test
    void entriesAreIntactWithSameIdsAfterRestart() {
        long createdA = entries.getEntry(a).getCreatedAt().toEpochMilli();
        vault.rotateVault(OLD.toCharArray(), NEW.toCharArray());

        EntryService restarted = restart(VaultServiceTest.FAST, NEW);
        assertEntriesIntact(restarted);
        assertEquals(createdA, restarted.getEntry(a).getCreatedAt().toEpochMilli());
    }

    @Test
    void everyCiphertextAndIvIsReplaced() {
        JdbcEntryDao raw = new JdbcEntryDao(cm);
        List<Entry> before = raw.findAll();
        vault.rotateVault(OLD.toCharArray(), NEW.toCharArray());
        List<Entry> after = raw.findAll();

        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).getId(), after.get(i).getId());
            assertFalse(java.util.Arrays.equals(before.get(i).getDataEnc(), after.get(i).getDataEnc()));
            assertFalse(java.util.Arrays.equals(before.get(i).getIv(), after.get(i).getIv()));
        }
    }

    @Test
    void sessionStaysUnlockedWithTheNewKey() {
        vault.rotateVault(OLD.toCharArray(), NEW.toCharArray());

        assertTrue(vault.isUnlocked());
        assertEntriesIntact(entries);
        long d = entries.addEntry("d.example.com", "dave", "Dave4Passw".toCharArray(), null);

        EntryService restarted = restart(VaultServiceTest.FAST, NEW);
        assertEquals("Dave4Passw", restarted.withPassword(d, String::new));
    }

    @Test
    void rotationUsesNewSaltAndUpgradesParamsToCurrentDefaults() {
        VaultMetadata before = metadata();
        assertEquals(VaultServiceTest.FAST, KdfParams.fromJson(before.getKdfParams()));

        // A newer build with stronger defaults opens the old vault, then rotates.
        VaultService upgradedBuild = new VaultService(new JdbcVaultMetadataDao(cm, clock), new JdbcVaultRotationDao(cm, clock), UPGRADED, clock);
        upgradedBuild.rotateVault(OLD.toCharArray(), NEW.toCharArray());

        VaultMetadata after = metadata();
        assertEquals(UPGRADED, KdfParams.fromJson(after.getKdfParams()));
        assertFalse(java.util.Arrays.equals(before.getKdfSalt(), after.getKdfSalt()));
        assertFalse(java.util.Arrays.equals(before.getAuthHash(), after.getAuthHash()));
        assertEntriesIntact(restart(VaultServiceTest.FAST, NEW));
    }

    @Test
    void passwordsAreWiped() {
        char[] oldPw = OLD.toCharArray();
        char[] newPw = NEW.toCharArray();
        vault.rotateVault(oldPw, newPw);
        assertArrayEquals(new char[oldPw.length], oldPw);
        assertArrayEquals(new char[newPw.length], newPw);
    }

    // --- old password: same throttling as unlock ---

    @Test
    void wrongOldPasswordIsCountedThrottledAndChangesNothing() {
        VaultMetadata before = metadata();

        InvalidPasswordException e = assertThrows(InvalidPasswordException.class,
                () -> vault.rotateVault(WRONG.toCharArray(), NEW.toCharArray()));
        assertEquals(1, e.getFailedAttempts());
        assertThrows(UnlockThrottledException.class, () -> vault.rotateVault(OLD.toCharArray(), NEW.toCharArray()));

        assertArrayEquals(before.getKdfSalt(), metadata().getKdfSalt());
        clock.advance(1_000);
        assertEntriesIntact(restart(VaultServiceTest.FAST, OLD));
    }

    @Test
    void weakNewPasswordIsRejectedBeforeAnything() {
        char[] oldPw = OLD.toCharArray();
        assertThrows(ValidationException.class, () -> vault.rotateVault(oldPw, "weak".toCharArray()));
        assertArrayEquals(new char[oldPw.length], oldPw, "wiped even on failure");
        assertEquals(0, metadata().getFailedAttempts());
        assertEntriesIntact(restart(VaultServiceTest.FAST, OLD));
    }

    // --- failures roll back: vault still opens with the old password ---

    @Test
    void failureWritingAnEntryMidTransactionRollsBack() {
        // Entry 1 is written, then entry 2 violates NOT NULL inside the same transaction.
        rotationDao.corrupt = r -> r.entries.get(1).setDataEnc(null);
        assertRotationFailsAndOldStillWorks(RuntimeException.class);
    }

    @Test
    void failureWritingMetadataAfterAllEntriesRollsBack() {
        rotationDao.corrupt = r -> r.metadata.setAuthHash(null);
        assertRotationFailsAndOldStillWorks(RuntimeException.class);
    }

    @Test
    void failureDuringReencryptionWritesNothing() {
        // Tamper one stored entry so decrypting it with the old key fails mid-way through re-encryption.
        JdbcEntryDao raw = new JdbcEntryDao(cm);
        Entry stored = raw.findById(c);
        byte[] original = stored.getDataEnc().clone();
        stored.getDataEnc()[0] ^= 1;
        raw.update(stored);

        assertThrows(CryptoException.class, () -> vault.rotateVault(OLD.toCharArray(), NEW.toCharArray()));

        stored.setDataEnc(original);
        raw.update(stored);
        assertEntriesIntact(restart(VaultServiceTest.FAST, OLD));
    }

    private void assertRotationFailsAndOldStillWorks(Class<? extends Throwable> expected) {
        VaultMetadata before = metadata();
        char[] oldPw = OLD.toCharArray();
        char[] newPw = NEW.toCharArray();

        assertThrows(expected, () -> vault.rotateVault(oldPw, newPw));

        assertArrayEquals(new char[oldPw.length], oldPw, "wiped even on failure");
        assertArrayEquals(new char[newPw.length], newPw, "wiped even on failure");
        VaultMetadata after = metadata();
        assertArrayEquals(before.getKdfSalt(), after.getKdfSalt());
        assertArrayEquals(before.getAuthHash(), after.getAuthHash());

        // The running session keeps working with the old key...
        assertTrue(vault.isUnlocked());
        assertEntriesIntact(entries);
        // ...and after a restart the old password opens everything; the new one does not.
        assertEntriesIntact(restart(VaultServiceTest.FAST, OLD));
        assertThrows(InvalidPasswordException.class, () -> restart(VaultServiceTest.FAST, NEW));
    }

    /** Wraps the real DAO; can corrupt the rotation result so a write fails inside the transaction. */
    private static final class FaultyRotationDao implements VaultRotationDao {
        private final VaultRotationDao delegate;
        Consumer<Rotation> corrupt = r -> {};

        FaultyRotationDao(VaultRotationDao delegate) {
            this.delegate = delegate;
        }

        @Override
        public void rotate(Function<List<Entry>, Rotation> reencrypt) {
            delegate.rotate(entries -> {
                Rotation r = reencrypt.apply(entries);
                corrupt.accept(r);
                return r;
            });
        }
    }
}
