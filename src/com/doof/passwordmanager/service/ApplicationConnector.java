package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.AccountDao;
import com.doof.passwordmanager.dao.VaultMetadataDao;
import com.doof.passwordmanager.model.Account;
import com.doof.passwordmanager.service.AccountService.AccountView;

import java.util.List;
import java.util.Objects;

public class ApplicationConnector {

    private final VaultService vaultService;
    private final AccountService accountService;

    public ApplicationConnector(VaultMetadataDao vaultMetadataDao, AccountDao accountDao) {
        Objects.requireNonNull(vaultMetadataDao);
        Objects.requireNonNull(accountDao);
        this.vaultService = new VaultService(vaultMetadataDao);
        this.accountService = new AccountService(accountDao, vaultService);
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


    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    public List<AccountView> getAllAccountsDecrypted() {
        return accountService.getAllAccountsDecrypted();
    }

    public Account getAccountById(int id) {
        return accountService.getAccountById(id);
    }

    public AccountView getAccountByIdDecrypted(int id) {
        return accountService.getAccountByIdDecrypted(id);
    }

    public int addAccount(String email, String website, char[] plaintextPassword) {
        return accountService.addAccount(email, website, plaintextPassword);
    }

    public void updateAccount(Account account, char[] plaintextPassword) {
        accountService.updateAccount(account, plaintextPassword);
    }

    public void deleteAccount(int id) {
        accountService.deleteAccount(id);
    }

    public List<Account> search(String query, int limit, int offset) {
        return accountService.search(query, limit, offset);
    }
}
