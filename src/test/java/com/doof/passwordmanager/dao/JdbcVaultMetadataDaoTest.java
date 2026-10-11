package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.VaultMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JdbcVaultMetadataDaoTest {

    private static final long T0 = 1_700_000_000_000L;

    @TempDir
    Path tmp;

    private MutableClock clock;
    private JdbcVaultMetadataDao dao;

    @BeforeEach
    void setUp() {
        ConnectionManager cm = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        clock = new MutableClock(T0);
        dao = new JdbcVaultMetadataDao(cm, clock);
    }

    @Test
    void findReturnsNullBeforeVaultIsCreated() {
        assertNull(dao.find());
    }

    @Test
    void insertThenFindRoundTripsAllFields() {
        VaultMetadata m = sample();
        dao.insert(m);

        assertEquals(T0, m.getCreatedAt());
        assertEquals(T0, m.getUpdatedAt());

        VaultMetadata found = dao.find();
        assertNotNull(found);
        assertArrayEquals(m.getKdfSalt(), found.getKdfSalt());
        assertEquals(m.getKdfParams(), found.getKdfParams());
        assertArrayEquals(m.getAuthHash(), found.getAuthHash());
        assertEquals(1, found.getEncVersion());
        assertEquals(0, found.getFailedAttempts());
        assertNull(found.getLastFailedAt());
        assertEquals(T0, found.getCreatedAt());
        assertEquals(T0, found.getUpdatedAt());
    }

    @Test
    void secondInsertIsRejected() {
        dao.insert(sample());
        assertThrows(RuntimeException.class, () -> dao.insert(sample()));
    }

    @Test
    void updatePersistsChangesAndKeepsCreatedAt() {
        dao.insert(sample());
        clock.advance(5_000);

        VaultMetadata m = dao.find();
        m.setKdfSalt(new byte[]{9, 9, 9});
        m.setKdfParams("{\"v\":2}");
        m.setAuthHash(new byte[]{7, 7});
        m.setEncVersion(2);
        m.setFailedAttempts(3);
        m.setLastFailedAt(T0 + 4_000);
        dao.update(m);

        VaultMetadata found = dao.find();
        assertArrayEquals(new byte[]{9, 9, 9}, found.getKdfSalt());
        assertEquals("{\"v\":2}", found.getKdfParams());
        assertArrayEquals(new byte[]{7, 7}, found.getAuthHash());
        assertEquals(2, found.getEncVersion());
        assertEquals(3, found.getFailedAttempts());
        assertEquals(T0 + 4_000, found.getLastFailedAt());
        assertEquals(T0, found.getCreatedAt());
        assertEquals(T0 + 5_000, found.getUpdatedAt());
    }

    @Test
    void updateCanClearLastFailedAt() {
        VaultMetadata m = sample();
        m.setFailedAttempts(2);
        m.setLastFailedAt(T0);
        dao.insert(m);

        m.setFailedAttempts(0);
        m.setLastFailedAt(null);
        dao.update(m);

        VaultMetadata found = dao.find();
        assertEquals(0, found.getFailedAttempts());
        assertNull(found.getLastFailedAt());
    }

    @Test
    void updateWithoutVaultFails() {
        assertThrows(RuntimeException.class, () -> dao.update(sample()));
    }

    @Test
    void dataSurvivesReopeningTheVaultFile() {
        dao.insert(sample());
        ConnectionManager reopened = ConnectionManager.open(tmp.resolve("vault.db"), w -> {});
        assertNotNull(new JdbcVaultMetadataDao(reopened).find());
    }

    private static VaultMetadata sample() {
        return new VaultMetadata(new byte[]{1, 2, 3, 4}, "{\"algorithm\":\"test\"}", new byte[]{5, 6, 7, 8}, 1);
    }
}
