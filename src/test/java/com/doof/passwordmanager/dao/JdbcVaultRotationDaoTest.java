package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.model.VaultMetadata;
import com.doof.passwordmanager.testutil.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdbcVaultRotationDaoTest {

    private static final long T0 = 1_700_000_000_000L;

    @TempDir
    Path tmp;

    private MutableClock clock;
    private JdbcVaultMetadataDao metaDao;
    private JdbcEntryDao entryDao;
    private JdbcVaultRotationDao dao;
    private Entry e1, e2;

    @BeforeEach
    void setUp() {
        ConnectionManager cm = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        clock = new MutableClock(T0);
        metaDao = new JdbcVaultMetadataDao(cm, clock);
        entryDao = new JdbcEntryDao(cm, clock);
        dao = new JdbcVaultRotationDao(cm, clock);

        metaDao.insert(new VaultMetadata(new byte[]{1}, "{\"old\":1}", new byte[]{2}, 1));
        metaDao.recordFailedUnlock(T0);
        e1 = entryDao.insert(id -> new Entry(new byte[]{10}, new byte[]{11}, 1));
        e2 = entryDao.insert(id -> new Entry(new byte[]{20}, new byte[]{21}, 1));
        clock.advance(5_000);
    }

    private static Entry reencrypted(Entry e, int marker) {
        Entry r = new Entry(new byte[]{(byte) marker}, new byte[]{(byte) (marker + 1)}, 1);
        r.setId(e.getId());
        return r;
    }

    @Test
    void successReplacesEntriesAndMetadata() {
        List<Long> seen = new ArrayList<>();
        dao.rotate(entries -> {
            entries.forEach(e -> seen.add(e.getId()));
            return new VaultRotationDao.Rotation(
                    new VaultMetadata(new byte[]{9}, "{\"new\":1}", new byte[]{8}, 1),
                    List.of(reencrypted(entries.get(0), 30), reencrypted(entries.get(1), 40)));
        });

        assertEquals(List.of(e1.getId(), e2.getId()), seen);
        assertArrayEquals(new byte[]{30}, entryDao.findById(e1.getId()).getDataEnc());
        assertArrayEquals(new byte[]{41}, entryDao.findById(e2.getId()).getIv());
        assertEquals(T0, entryDao.findById(e1.getId()).getUpdatedAt(), "entry updated_at untouched");

        VaultMetadata m = metaDao.find();
        assertArrayEquals(new byte[]{9}, m.getKdfSalt());
        assertEquals("{\"new\":1}", m.getKdfParams());
        assertArrayEquals(new byte[]{8}, m.getAuthHash());
        assertEquals(0, m.getFailedAttempts());
        assertNull(m.getLastFailedAt());
        assertEquals(T0 + 5_000, m.getUpdatedAt());
    }

    @Test
    void failureOnSecondEntryRollsBackTheFirst() {
        assertThrows(RuntimeException.class, () -> dao.rotate(entries -> {
            Entry bad = reencrypted(entries.get(1), 40);
            bad.setDataEnc(null);  // NOT NULL violation after entry 1 was already updated
            return new VaultRotationDao.Rotation(
                    new VaultMetadata(new byte[]{9}, "{}", new byte[]{8}, 1),
                    List.of(reencrypted(entries.get(0), 30), bad));
        }));

        assertArrayEquals(new byte[]{10}, entryDao.findById(e1.getId()).getDataEnc());
        assertArrayEquals(new byte[]{1}, metaDao.find().getKdfSalt());
        assertEquals(1, metaDao.find().getFailedAttempts());
    }

    @Test
    void failureOnMetadataRollsBackAllEntries() {
        assertThrows(RuntimeException.class, () -> dao.rotate(entries -> new VaultRotationDao.Rotation(
                new VaultMetadata(new byte[]{9}, "{}", null, 1),
                List.of(reencrypted(entries.get(0), 30), reencrypted(entries.get(1), 40)))));

        assertArrayEquals(new byte[]{10}, entryDao.findById(e1.getId()).getDataEnc());
        assertArrayEquals(new byte[]{20}, entryDao.findById(e2.getId()).getDataEnc());
        assertArrayEquals(new byte[]{2}, metaDao.find().getAuthHash());
    }

    @Test
    void mustReencryptExactlyTheStoredEntries() {
        assertThrows(IllegalStateException.class, () -> dao.rotate(entries -> new VaultRotationDao.Rotation(
                new VaultMetadata(new byte[]{9}, "{}", new byte[]{8}, 1),
                List.of(reencrypted(entries.get(0), 30)))));  // e2 missing
        assertArrayEquals(new byte[]{10}, entryDao.findById(e1.getId()).getDataEnc());
    }
}
