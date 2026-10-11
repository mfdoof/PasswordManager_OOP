package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.service.EntryService.EntrySummary;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ApplicationConnector {

    private final VaultService vaultService;
    private final EntryService entryService;

    public ApplicationConnector(VaultMetadataDao vaultMetadataDao, EntryDao entryDao) {
        Objects.requireNonNull(vaultMetadataDao);
        Objects.requireNonNull(entryDao);
        this.vaultService = new VaultService(vaultMetadataDao);
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
