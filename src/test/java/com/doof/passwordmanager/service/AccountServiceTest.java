package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.CryptoException;
import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    @TempDir
    Path tmp;

    private Path vaultFile;
    private JdbcEntryDao entryDao;
    private VaultService vault;
    private AccountService accounts;

    @BeforeEach
    void setUp() {
        vaultFile = tmp.resolve("vault.db");
        ConnectionManager cm = ConnectionManager.open(vaultFile, w -> {});
        entryDao = new JdbcEntryDao(cm);
        vault = new VaultService(new JdbcVaultMetadataDao(cm), VaultServiceTest.FAST);
        accounts = new AccountService(entryDao, vault);
        vault.createVault("MasterPass1".toCharArray());
    }

    @Test
    void roundTripThroughLockAndUnlock() {
        long id = accounts.addAccount("me@example.com", "example.com", "Hunter2Pass".toCharArray());
        vault.lockVault();
        vault.unlockVault("MasterPass1".toCharArray());

        AccountService.AccountView v = accounts.getAccountByIdDecrypted(id);
        assertEquals("me@example.com", v.getEmail());
        assertEquals("example.com", v.getWebsite());
        assertArrayEquals("Hunter2Pass".toCharArray(), v.getPassword());
    }

    @Test
    void vaultFileContainsNoPlaintext() throws Exception {
        accounts.addAccount("me@example.com", "example.com", "Hunter2Pass".toCharArray());
        String raw = new String(Files.readAllBytes(vaultFile), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("example.com"));
        assertFalse(raw.contains("Hunter2Pass"));
    }

    @Test
    void lockedVaultRefusesAccess() {
        long id = accounts.addAccount("me@example.com", "example.com", "Hunter2Pass".toCharArray());
        vault.lockVault();
        assertThrows(RuntimeException.class, () -> accounts.getAccountByIdDecrypted(id));
        assertThrows(RuntimeException.class,
                () -> accounts.addAccount("you@example.com", "example.org", "Hunter2Pass".toCharArray()));
    }

    @Test
    void tamperedCiphertextIsDetected() {
        long id = accounts.addAccount("me@example.com", "example.com", "Hunter2Pass".toCharArray());
        Entry stored = entryDao.findById(id);
        stored.getDataEnc()[0] ^= 0x01;
        entryDao.update(stored);

        assertThrows(CryptoException.class, () -> accounts.getAccountByIdDecrypted(id));
    }

    @Test
    void ciphertextSwappedBetweenRowsIsDetected() {
        long a = accounts.addAccount("a@example.com", "a.example.com", "Hunter2Pass".toCharArray());
        long b = accounts.addAccount("b@example.com", "b.example.com", "Hunter3Pass".toCharArray());

        // Attacker copies row a's (valid) ciphertext + IV into row b.
        Entry rowA = entryDao.findById(a);
        Entry rowB = entryDao.findById(b);
        rowB.setDataEnc(rowA.getDataEnc());
        rowB.setIv(rowA.getIv());
        entryDao.update(rowB);

        assertThrows(CryptoException.class, () -> accounts.getAccountByIdDecrypted(b));
        assertEquals("a@example.com", accounts.getAccountByIdDecrypted(a).getEmail());
    }

    @Test
    void updateReencryptsWithFreshIvAndKeepsPasswordWhenBlank() {
        long id = accounts.addAccount("me@example.com", "example.com", "Hunter2Pass".toCharArray());
        byte[] ivBefore = entryDao.findById(id).getIv();

        accounts.updateAccount(id, "new@example.com", "example.com", null);

        assertFalse(java.util.Arrays.equals(ivBefore, entryDao.findById(id).getIv()));
        AccountService.AccountView v = accounts.getAccountByIdDecrypted(id);
        assertEquals("new@example.com", v.getEmail());
        assertArrayEquals("Hunter2Pass".toCharArray(), v.getPassword());
    }

    @Test
    void duplicateAndSearchWorkOnDecryptedData() {
        accounts.addAccount("me@example.com", "example.com", "Hunter2Pass".toCharArray());
        accounts.addAccount("me@example.com", "other.org", "Hunter2Pass".toCharArray());

        assertThrows(ValidationException.class,
                () -> accounts.addAccount("ME@example.com", "Example.com", "Hunter2Pass".toCharArray()));
        assertEquals(1, accounts.search("OTHER", 10, 0).size());
        assertEquals(2, accounts.search("me@", 10, 0).size());
    }
}
