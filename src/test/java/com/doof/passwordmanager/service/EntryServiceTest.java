package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.CryptoException;
import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.service.EntryService.EntrySummary;
import com.doof.passwordmanager.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EntryServiceTest {

    @TempDir
    Path tmp;

    private Path vaultFile;
    private JdbcEntryDao entryDao;
    private CountingEntryDao countingDao;
    private VaultService vault;
    private EntryService entries;

    @BeforeEach
    void setUp() {
        vaultFile = tmp.resolve("vault.db");
        ConnectionManager cm = ConnectionManager.open(vaultFile, w -> {});
        entryDao = new JdbcEntryDao(cm);
        countingDao = new CountingEntryDao(entryDao);
        vault = new VaultService(new JdbcVaultMetadataDao(cm), VaultServiceTest.FAST);
        entries = new EntryService(countingDao, vault);
        vault.createVault("MasterPass1".toCharArray());
    }

    // --- list / search never expose passwords ---

    @Test
    void summaryTypeHasNoPasswordOrNotes() {
        Set<String> fields = Arrays.stream(EntrySummary.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("id", "site", "user", "createdAt", "updatedAt"), fields);
    }

    @Test
    void listAndSearchNeverContainThePassword() {
        entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), "my secret note");

        List<EntrySummary> listed = entries.listEntries();
        List<EntrySummary> found = entries.search("example", 10, 0);

        assertEquals(1, listed.size());
        assertEquals(1, found.size());
        for (EntrySummary e : List.of(listed.get(0), found.get(0), entries.getEntry(listed.get(0).getId()))) {
            assertEquals("example.com", e.getSite());
            assertEquals("me@example.com", e.getUser());
            assertFalse(e.toString().contains("Hunter2Pass"));
            assertFalse(e.toString().contains("my secret note"));
        }
    }

    @Test
    void searchMatchesSiteOrUserCaseInsensitivelyWithPaging() {
        entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        entries.addEntry("other.org", "me@example.com", "Hunter2Pass".toCharArray(), null);
        entries.addEntry("third.net", "x@third.net", "Hunter2Pass".toCharArray(), null);

        assertEquals(1, entries.search("OTHER", 10, 0).size());
        assertEquals(2, entries.search("me@", 10, 0).size());
        assertEquals(3, entries.search("", 10, 0).size());
        assertEquals("other.org", entries.search("me@", 1, 1).get(0).getSite());
        assertEquals(1, entries.search("me@", 1, 0).size());
        assertTrue(entries.search("nomatch", 10, 0).isEmpty());
    }

    @Test
    void indexIsLoadedOnceAndServesListingWithoutReadingTheDb() {
        entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        vault.lockVault();
        vault.unlockVault("MasterPass1".toCharArray());
        entries.loadIndex();
        int loads = countingDao.findAllCalls.get();

        entries.listEntries();
        entries.search("ex", 10, 0);
        entries.listEntries();

        assertEquals(loads, countingDao.findAllCalls.get());
    }

    @Test
    void lockDropsTheIndexAndBlocksAccess() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        vault.lockVault();

        assertThrows(RuntimeException.class, () -> entries.listEntries());
        assertThrows(RuntimeException.class, () -> entries.search("ex", 10, 0));
        assertThrows(RuntimeException.class, () -> entries.withPassword(id, String::new));
        assertThrows(RuntimeException.class, () -> entries.revealNotes(id));
    }

    // --- reveal on demand ---

    @Test
    void withPasswordReturnsTheRightPassword() {
        long a = entries.addEntry("a.example.com", "a@example.com", "Hunter2Pass".toCharArray(), null);
        long b = entries.addEntry("b.example.com", "b@example.com", "Other3Pass".toCharArray(), null);

        assertEquals("Hunter2Pass", entries.withPassword(a, String::new));
        assertEquals("Other3Pass", entries.withPassword(b, String::new));
    }

    @Test
    void passwordIsWipedAfterUse() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        AtomicReference<char[]> seen = new AtomicReference<>();

        entries.withPassword(id, pw -> {
            assertArrayEquals("Hunter2Pass".toCharArray(), pw);
            seen.set(pw);  // test-only: keep the reference to check it afterwards
            return null;
        });

        assertArrayEquals(new char["Hunter2Pass".length()], seen.get());
    }

    @Test
    void passwordIsWipedEvenIfTheCallbackThrows() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        AtomicReference<char[]> seen = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> entries.withPassword(id, pw -> {
            seen.set(pw);
            throw new IllegalStateException("boom");
        }));

        assertArrayEquals(new char["Hunter2Pass".length()], seen.get());
    }

    @Test
    void inputPasswordArraysAreWiped() {
        char[] added = "Hunter2Pass".toCharArray();
        long id = entries.addEntry("example.com", "me@example.com", added, null);
        assertArrayEquals(new char[added.length], added);

        char[] changed = "Changed9Pass".toCharArray();
        entries.updateEntry(id, "example.com", "me@example.com", changed, null);
        assertArrayEquals(new char[changed.length], changed);
        assertEquals("Changed9Pass", entries.withPassword(id, String::new));
    }

    @Test
    void notesAreStoredAndRevealedOnDemand() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), "line1\nline2");
        assertEquals("line1\nline2", entries.revealNotes(id));

        long none = entries.addEntry("other.org", "me@example.com", "Hunter2Pass".toCharArray(), null);
        assertNull(entries.revealNotes(none));
    }

    @Test
    void updateKeepsPasswordAndNotesWhenNotGiven() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), "keep me");
        byte[] ivBefore = entryDao.findById(id).getIv();

        entries.updateEntry(id, "example.com", "new@example.com", null, null);

        assertFalse(Arrays.equals(ivBefore, entryDao.findById(id).getIv()), "must re-encrypt with a fresh IV");
        assertEquals("new@example.com", entries.getEntry(id).getUser());
        assertEquals("Hunter2Pass", entries.withPassword(id, String::new));
        assertEquals("keep me", entries.revealNotes(id));

        entries.updateEntry(id, "example.com", "new@example.com", null, "changed");
        assertEquals("changed", entries.revealNotes(id));
    }

    @Test
    void deleteRemovesFromIndexAndDb() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        entries.deleteEntry(id);

        assertNull(entries.getEntry(id));
        assertTrue(entries.listEntries().isEmpty());
        assertNull(entryDao.findById(id));
    }

    @Test
    void duplicateSiteAndUserIsRejected() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        long other = entries.addEntry("other.org", "me@example.com", "Hunter2Pass".toCharArray(), null);

        assertThrows(ValidationException.class,
                () -> entries.addEntry("Example.com", "ME@example.com", "Hunter2Pass".toCharArray(), null));
        assertThrows(ValidationException.class,
                () -> entries.updateEntry(other, "example.com", "me@example.com", null, null));
        entries.updateEntry(id, "EXAMPLE.com", "me@example.com", null, null);  // same entry: not a duplicate
    }

    // --- crypto through the service ---

    @Test
    void roundTripThroughLockAndUnlock() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        vault.lockVault();
        vault.unlockVault("MasterPass1".toCharArray());

        EntrySummary e = entries.getEntry(id);
        assertEquals("me@example.com", e.getUser());
        assertEquals("example.com", e.getSite());
        assertEquals("Hunter2Pass", entries.withPassword(id, String::new));
    }

    @Test
    void vaultFileContainsNoPlaintext() throws Exception {
        entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), "a note");
        String raw = new String(Files.readAllBytes(vaultFile), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("example.com"));
        assertFalse(raw.contains("Hunter2Pass"));
        assertFalse(raw.contains("a note"));
    }

    @Test
    void tamperedCiphertextIsDetected() {
        long id = entries.addEntry("example.com", "me@example.com", "Hunter2Pass".toCharArray(), null);
        Entry stored = entryDao.findById(id);
        stored.getDataEnc()[0] ^= 0x01;
        entryDao.update(stored);

        assertThrows(CryptoException.class, () -> entries.withPassword(id, String::new));
        assertThrows(CryptoException.class, () -> entries.loadIndex());
    }

    @Test
    void ciphertextSwappedBetweenRowsIsDetected() {
        long a = entries.addEntry("a.example.com", "a@example.com", "Hunter2Pass".toCharArray(), null);
        long b = entries.addEntry("b.example.com", "b@example.com", "Hunter3Pass".toCharArray(), null);

        // Attacker copies row a's (valid) ciphertext + IV into row b.
        Entry rowA = entryDao.findById(a);
        Entry rowB = entryDao.findById(b);
        rowB.setDataEnc(rowA.getDataEnc());
        rowB.setIv(rowA.getIv());
        entryDao.update(rowB);

        assertThrows(CryptoException.class, () -> entries.withPassword(b, String::new));
        assertEquals("Hunter2Pass", entries.withPassword(a, String::new));
    }

    /** Delegating DAO that counts findAll() calls. */
    private static final class CountingEntryDao implements EntryDao {
        private final EntryDao delegate;
        final AtomicInteger findAllCalls = new AtomicInteger();

        CountingEntryDao(EntryDao delegate) {
            this.delegate = delegate;
        }

        @Override public Entry insert(LongFunction<Entry> seal) { return delegate.insert(seal); }
        @Override public Entry findById(long id) { return delegate.findById(id); }
        @Override public List<Entry> findAll() { findAllCalls.incrementAndGet(); return delegate.findAll(); }
        @Override public void update(Entry entry) { delegate.update(entry); }
        @Override public void delete(long id) { delegate.delete(id); }
    }
}
