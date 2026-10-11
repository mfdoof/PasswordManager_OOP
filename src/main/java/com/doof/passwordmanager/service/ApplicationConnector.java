package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.service.AccountService.AccountView;

import java.util.List;
import java.util.Objects;

public class ApplicationConnector {

    private final VaultService vaultService;
    private final AccountService accountService;

    public ApplicationConnector(VaultMetadataDao vaultMetadataDao, EntryDao entryDao) {
        Objects.requireNonNull(vaultMetadataDao);
        Objects.requireNonNull(entryDao);
        this.vaultService = new VaultService(vaultMetadataDao);
        this.accountService = new AccountService(entryDao, vaultService);
    }


    public boolean isVaultInitialized() {
        return vaultService.isVaultInitialized();
    }

    public void createVault(char[] masterPassword) {
        vaultService.createVault(masterPassword);
    }

    public void unlockVault(char[] masterPassword) {
        vaultService.unlockVault(masterPassword);
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


    public List<AccountView> getAllAccountsDecrypted() {
        return accountService.getAllAccountsDecrypted();
    }

    public AccountView getAccountByIdDecrypted(long id) {
        return accountService.getAccountByIdDecrypted(id);
    }

    public long addAccount(String email, String website, char[] plaintextPassword) {
        return accountService.addAccount(email, website, plaintextPassword);
    }

    public void updateAccount(long id, String email, String website, char[] plaintextPassword) {
        accountService.updateAccount(id, email, website, plaintextPassword);
    }

    public void deleteAccount(long id) {
        accountService.deleteAccount(id);
    }

    public List<AccountView> search(String query, int limit, int offset) {
        return accountService.search(query, limit, offset);
    }
}
