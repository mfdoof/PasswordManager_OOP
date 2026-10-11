package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.dao.VaultRotationDao;
import com.doof.passwordmanager.service.EntryService.EntrySummary;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ApplicationConnector {

    private final VaultService vaultService;
    private final EntryService entryService;

    public ApplicationConnector(VaultMetadataDao vaultMetadataDao, VaultRotationDao rotationDao, EntryDao entryDao) {
        this(vaultMetadataDao, rotationDao, entryDao, KdfParams.DEFAULT, Clock.systemUTC());
    }

    /** Full constructor: KDF params for new vaults / rotation and the clock used for throttling (tests). */
    public ApplicationConnector(VaultMetadataDao vaultMetadataDao, VaultRotationDao rotationDao, EntryDao entryDao,
                                KdfParams newVaultParams, Clock clock) {
        Objects.requireNonNull(vaultMetadataDao);
        Objects.requireNonNull(rotationDao);
        Objects.requireNonNull(entryDao);
        this.vaultService = new VaultService(vaultMetadataDao, rotationDao, newVaultParams, clock);
        this.entryService = new EntryService(entryDao, vaultService);
    }


    public boolean isVaultInitialized() {
        return vaultService.isVaultInitialized();
    }

    public void createVault(char[] masterPassword) {
        vaultService.createVault(masterPassword);
    }

    public void unlockVault(char[] masterPassword) {
        vaultService.unlockVault(masterPassword);
        entryService.loadIndex();
    }

    /** Failed-attempt count and remaining wait before the next unlock is allowed. */
    public UnlockStatus getUnlockStatus() {
        return vaultService.unlockStatus();
    }

    /**
     * Changes the master password: re-encrypts all entries in one transaction, all-or-nothing.
     * Wipes both arrays. Throws {@link InvalidPasswordException} / {@link UnlockThrottledException}
     * like unlock. The session stays unlocked with the new key.
     */
    public void changeMasterPassword(char[] oldPassword, char[] newPassword) {
        entryService.exclusively(() -> {
            vaultService.rotateVault(oldPassword, newPassword);
            return null;
        });
    }

    public void lockVault() {
        vaultService.lockVault();
    }

    public boolean isUnlocked() {
        return vaultService.isUnlocked();
    }

    public void resetSessionTimer() {
        try {
            vaultService.resetSessionTimer();
        } catch (Exception ignored) { }
    }


    /** Site + user of every entry; no passwords. */
    public List<EntrySummary> listEntries() {
        return entryService.listEntries();
    }

    public EntrySummary getEntry(long id) {
        return entryService.getEntry(id);
    }

    public List<EntrySummary> search(String query, int limit, int offset) {
        return entryService.search(query, limit, offset);
    }

    /** Decrypts the password on demand; it is wiped as soon as {@code use} returns. */
    public <T> T withPassword(long id, Function<char[], T> use) {
        return entryService.withPassword(id, use);
    }

    public String revealNotes(long id) {
        return entryService.revealNotes(id);
    }

    public long addEntry(String site, String user, char[] password, String notes) {
        return entryService.addEntry(site, user, password, notes);
    }

    public void updateEntry(long id, String site, String user, char[] newPassword, String notes) {
        entryService.updateEntry(id, site, user, newPassword, notes);
    }

    public void deleteEntry(long id) {
        entryService.deleteEntry(id);
    }
}
