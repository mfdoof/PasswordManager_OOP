package com.doof.passwordmanager.dao;

import com.doof.passwordmanager.model.Account;
import java.util.List;

public interface AccountDao {
    void addAccount(Account account);
    List<Account> getAllAccounts();
    Account getAccountById(int id);
    void updateAccount(Account account);
    void deleteAccount(int id);
    List<Account> search(String query, int limit, int offset);
}
