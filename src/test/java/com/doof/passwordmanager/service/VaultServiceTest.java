package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.VaultMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VaultServiceTest {

    static final KdfParams FAST = new KdfParams(1024, 1, 1);

    @TempDir
    Path tmp;

    private JdbcVaultMetadataDao dao;
    private VaultService vault;

    @BeforeEach
    void setUp() {
        dao = new JdbcVaultMetadataDao(ConnectionManager.open(tmp.resolve("vault.db"), w -> {}));
        vault = new VaultService(dao, FAST);
    }

    @Test
    void createLeavesVaultUnlockedAndStoresArgon2Metadata() {
        vault.createVault("MasterPass1".toCharArray());

        assertTrue(vault.isUnlocked());
        VaultMetadata m = dao.find();
        assertEquals(16, m.getKdfSalt().length);
        assertEquals(32, m.getAuthHash().length);
        assertEquals(VaultService.ENC_VERSION, m.getEncVersion());
        assertEquals(FAST, KdfParams.fromJson(m.getKdfParams()));
    }

    @Test
    void createWipesThePasswordArray() {
        char[] pw = "MasterPass1".toCharArray();
        vault.createVault(pw);
        assertArrayEquals(new char[pw.length], pw);
    }

    @Test
    void unlockWithCorrectPasswordAfterLock() {
        vault.createVault("MasterPass1".toCharArray());
        vault.lockVault();
        assertFalse(vault.isUnlocked());

        vault.unlockVault("MasterPass1".toCharArray());
        assertTrue(vault.isUnlocked());
    }

    @Test
    void wrongPasswordIsRejectedAndVaultStaysLocked() {
        vault.createVault("MasterPass1".toCharArray());
        vault.lockVault();

        RuntimeException e = assertThrows(RuntimeException.class, () -> vault.unlockVault("MasterPass2".toCharArray()));
        assertEquals("Invalid master password", e.getMessage());
        assertFalse(vault.isUnlocked());
    }

    @Test
    void unlockUsesParamsStoredInKdfParamsNotCurrentDefaults() {
        vault.createVault("MasterPass1".toCharArray());
        vault.lockVault();

        // A build with different defaults must still unlock an existing vault.
        VaultService otherDefaults = new VaultService(dao, new KdfParams(2048, 2, 1));
        otherDefaults.unlockVault("MasterPass1".toCharArray());
        assertTrue(otherDefaults.isUnlocked());
        assertEquals(FAST, KdfParams.fromJson(dao.find().getKdfParams()));
    }

    @Test
    void sameEncKeyAfterReunlock() {
        vault.createVault("MasterPass1".toCharArray());
        byte[] first = vault.withEncKey(byte[]::clone);
        vault.lockVault();
        vault.unlockVault("MasterPass1".toCharArray());
        assertArrayEquals(first, vault.withEncKey(byte[]::clone));
        assertFalse(java.util.Arrays.equals(first, dao.find().getAuthHash()), "enc key must differ from stored auth key");
    }

    @Test
    void lockWipesTheEncKey() {
        vault.createVault("MasterPass1".toCharArray());
        AtomicReference<byte[]> keyRef = new AtomicReference<>();
        vault.withEncKey(k -> { keyRef.set(k); return null; });  // test-only: peek at the live array
        assertNotEquals(0, sum(keyRef.get()));

        vault.lockVault();

        assertEquals(0, sum(keyRef.get()));
        assertThrows(RuntimeException.class, () -> vault.withEncKey(k -> null));
    }

    @Test
    void cachedMetadataSaltIsNotWiped() {
        vault.createVault("MasterPass1".toCharArray());
        assertArrayEquals(dao.find().getKdfSalt(), vault.getMetadata().getKdfSalt());
        assertNotEquals(0, sum(vault.getMetadata().getKdfSalt()));
    }

    @Test
    void autoLockThreadIsDaemon() {
        vault.createVault("MasterPass1".toCharArray());  // starts the auto-lock timer
        Thread t = Thread.getAllStackTraces().keySet().stream()
                .filter(th -> th.getName().equals("doof-autolock"))
                .findFirst().orElseThrow();
        assertTrue(t.isDaemon());
    }

    @Test
    void secondCreateIsRejected() {
        vault.createVault("MasterPass1".toCharArray());
        assertThrows(RuntimeException.class, () -> vault.createVault("MasterPass1".toCharArray()));
    }

    private static int sum(byte[] b) {
        int s = 0;
        for (byte x : b) s |= x;
        return s;
    }
}
