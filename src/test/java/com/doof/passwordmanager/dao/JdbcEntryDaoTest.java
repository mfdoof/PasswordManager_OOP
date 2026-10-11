package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class JdbcEntryDaoTest {

    private static final long T0 = 1_700_000_000_000L;

    @TempDir
    Path tmp;

    private MutableClock clock;
    private JdbcEntryDao dao;

    @BeforeEach
    void setUp() {
        ConnectionManager cm = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        clock = new MutableClock(T0);
        dao = new JdbcEntryDao(cm, clock);
    }

    @Test
    void insertAssignsIdAndTimestamps() {
        Entry e = insert(1);

        assertTrue(e.getId() > 0);
        assertEquals(T0, e.getCreatedAt());
        assertEquals(T0, e.getUpdatedAt());
    }

    @Test
    void sealReceivesTheIdThatIsStored() {
        AtomicLong seen = new AtomicLong();
        Entry e = dao.insert(id -> {
            seen.set(id);
            return new Entry(new byte[]{(byte) id}, new byte[]{1}, 1);
        });

        assertEquals(seen.get(), e.getId());
        assertArrayEquals(new byte[]{(byte) e.getId()}, dao.findById(e.getId()).getDataEnc());
    }

    @Test
    void failedSealStoresNothing() {
        assertThrows(IllegalStateException.class, () -> dao.insert(id -> {
            throw new IllegalStateException("boom");
        }));
        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    void idsAreNeverReusedAfterDelete() {
        // The id is the AES-GCM AAD, so a reused id would let an old ciphertext validate in a new row.
        Entry a = insert(1);
        Entry b = insert(2);
        dao.delete(b.getId());

        Entry c = insert(3);
        assertTrue(c.getId() > b.getId(), "id " + c.getId() + " reused");
        assertNotEquals(a.getId(), c.getId());
    }

    @Test
    void findByIdRoundTripsBytesExactly() {
        // Include every byte value so nothing is treated as text or truncated at NUL.
        byte[] blob = new byte[256];
        for (int i = 0; i < blob.length; i++) blob[i] = (byte) i;
        byte[] iv = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        Entry e = dao.insert(id -> new Entry(blob, iv, 1));

        Entry found = dao.findById(e.getId());
        assertNotNull(found);
        assertEquals(e.getId(), found.getId());
        assertArrayEquals(blob, found.getDataEnc());
        assertArrayEquals(iv, found.getIv());
        assertEquals(1, found.getEncVersion());
        assertEquals(T0, found.getCreatedAt());
        assertEquals(T0, found.getUpdatedAt());
    }

    @Test
    void findByIdReturnsNullForMissingEntry() {
        assertNull(dao.findById(42));
    }

    @Test
    void findAllReturnsEntriesInIdOrder() {
        assertTrue(dao.findAll().isEmpty());

        Entry a = insert(1), b = insert(2), c = insert(3);

        List<Entry> all = dao.findAll();
        assertEquals(List.of(a.getId(), b.getId(), c.getId()), all.stream().map(Entry::getId).toList());
        assertArrayEquals(new byte[]{2}, all.get(1).getDataEnc());
    }

    @Test
    void updateReplacesBlobAndBumpsUpdatedAt() {
        Entry e = insert(1);
        clock.advance(1_000);

        e.setDataEnc(new byte[]{9, 9});
        e.setIv(new byte[]{8});
        e.setEncVersion(2);
        dao.update(e);
        assertEquals(T0 + 1_000, e.getUpdatedAt());

        Entry found = dao.findById(e.getId());
        assertArrayEquals(new byte[]{9, 9}, found.getDataEnc());
        assertArrayEquals(new byte[]{8}, found.getIv());
        assertEquals(2, found.getEncVersion());
        assertEquals(T0, found.getCreatedAt());
        assertEquals(T0 + 1_000, found.getUpdatedAt());
    }

    @Test
    void updateMissingEntryFails() {
        Entry ghost = entry(1);
        ghost.setId(999);
        assertThrows(RuntimeException.class, () -> dao.update(ghost));
    }

    @Test
    void deleteRemovesOnlyThatEntry() {
        Entry a = insert(1), b = insert(2);

        dao.delete(a.getId());

        assertNull(dao.findById(a.getId()));
        assertNotNull(dao.findById(b.getId()));
        assertEquals(1, dao.findAll().size());
    }

    @Test
    void deleteMissingEntryFails() {
        assertThrows(RuntimeException.class, () -> dao.delete(999));
    }

    @Test
    void requiredColumnsRejectNull() {
        assertThrows(RuntimeException.class, () -> dao.insert(id -> new Entry(null, new byte[]{1}, 1)));
        assertThrows(RuntimeException.class, () -> dao.insert(id -> new Entry(new byte[]{1}, null, 1)));
        assertTrue(dao.findAll().isEmpty());
    }

    private Entry insert(int marker) {
        return dao.insert(id -> entry(marker));
    }

    private static Entry entry(int marker) {
        return new Entry(new byte[]{(byte) marker}, new byte[]{(byte) (100 + marker)}, 1);
    }
}
