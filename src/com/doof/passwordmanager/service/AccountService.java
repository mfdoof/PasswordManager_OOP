package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.AccountDao;
import com.doof.passwordmanager.model.Account;
import com.doof.passwordmanager.util.InputValidator;
import com.doof.passwordmanager.util.PasswordEncryptor;
import com.doof.passwordmanager.util.ValidationException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AccountService {
    public static class AccountView {
        private final int id;
        private final String email;
        private final String website;
        private final char[] password;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public AccountView(int id, String email, String website, char[] password, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id;
            this.email = email;
            this.website = website;
            this.password = password;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public int getId() { return id; }
        public String getEmail() { return email; }
        public String getWebsite() { return website; }
        public char[] getPassword() { return password; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    private final AccountDao accountDao;
    private final VaultService vaultService;

    public AccountService(AccountDao accountDao, VaultService vaultService) {
        this.accountDao = accountDao;
        this.vaultService = vaultService;
    }

    public List<Account> getAllAccounts() {
        return accountDao.getAllAccounts();
    }

    public Account getAccountById(int id) {
        return accountDao.getAccountById(id);
    }

    public List<AccountView> getAllAccountsDecrypted() {
        checkUnlocked();
        List<Account> rows = accountDao.getAllAccounts();
        List<AccountView> result = new ArrayList<>(rows.size());
        for (Account stored : rows) {
            char[] plain = null;
            try {
                plain = decryptPasswordBytesToCharArray(stored.getPasswordEncrypted(), stored.getIv());
                result.add(new AccountView(
                        stored.getId(),
                        stored.getEmail(),
                        stored.getWebsite(),
                        plain,
                        stored.getCreatedAt(),
                        stored.getUpdatedAt()
                ));
                plain = null;
            } finally {
                if (plain != null) Arrays.fill(plain, '\0');
            }
        }
        return result;
    }

    public AccountView getAccountByIdDecrypted(int id) {
        checkUnlocked();
        Account stored = accountDao.getAccountById(id);
        if (stored == null) return null;
        char[] plain = null;
        try {
            plain = decryptPasswordBytesToCharArray(stored.getPasswordEncrypted(), stored.getIv());
            return new AccountView(
                    stored.getId(),
                    stored.getEmail(),
                    stored.getWebsite(),
                    plain,
                    stored.getCreatedAt(),
                    stored.getUpdatedAt()
            );
        } finally {
            if (plain != null) Arrays.fill(plain, '\0');
        }
    }

    public int addAccount(String email, String website, char[] plaintextPassword) {
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(website, "website is required");
        if (plaintextPassword == null || plaintextPassword.length == 0) {
            throw new IllegalArgumentException("plaintextPassword is required");
        }

        email = InputValidator.sanitize(email);
        website = InputValidator.sanitize(website);

        InputValidator.validateEmail(email);
        InputValidator.validateWebsite(website);
        InputValidator.validatePassword(plaintextPassword);
        InputValidator.checkDuplicate(accountDao, email, website);

        checkUnlocked();
        EncryptedData ed = null;
        try {
            ed = encryptPassword(plaintextPassword);
            Account account = new Account();
            account.setEmail(email);
            account.setWebsite(website);
            account.setPasswordEncrypted(ed.ciphertext);
            account.setIv(ed.iv);
            account.setEncVersion(vaultService.getMetadata().getEncVersion());
            accountDao.addAccount(account);
            return account.getId();
        } finally {
            if (ed != null) {
                if (ed.ciphertext != null) Arrays.fill(ed.ciphertext, (byte) 0);
                if (ed.iv != null) Arrays.fill(ed.iv, (byte) 0);
            }
            Arrays.fill(plaintextPassword, '\0');
        }
    }

    public void updateAccount(Account account, char[] plaintextPassword) {
        Objects.requireNonNull(account, "account is required");
        checkUnlocked();

        String email = InputValidator.sanitize(account.getEmail());
        String website = InputValidator.sanitize(account.getWebsite());
        InputValidator.validateEmail(email);
        InputValidator.validateWebsite(website);

        Account existing = accountDao.getAccountById(account.getId());
        if (existing == null) {
            throw new RuntimeException("Account not found: " + account.getId());
        }

        boolean changedEmailOrWebsite = !existing.getEmail().equalsIgnoreCase(email)
                || !existing.getWebsite().equalsIgnoreCase(website);

        if (changedEmailOrWebsite) {
            List<Account> found = accountDao.search(email, 10, 0);
            boolean duplicate = found.stream()
                    .anyMatch(a -> a.getEmail().equalsIgnoreCase(email) && a.getWebsite().equalsIgnoreCase(website) && a.getId() != account.getId());
            if (duplicate) {
                throw new ValidationException("An account with this email and website already exists.");
            }
            account.setEmail(email);
            account.setWebsite(website);
        }

        if (plaintextPassword != null && plaintextPassword.length > 0) {
            InputValidator.validatePassword(plaintextPassword);
            EncryptedData ed = null;
            try {
                ed = encryptPassword(plaintextPassword);
                account.setPasswordEncrypted(ed.ciphertext);
                account.setIv(ed.iv);
                account.setEncVersion(vaultService.getMetadata().getEncVersion());
                accountDao.updateAccount(account);
            } finally {
                if (ed != null) {
                    if (ed.ciphertext != null) Arrays.fill(ed.ciphertext, (byte) 0);
                    if (ed.iv != null) Arrays.fill(ed.iv, (byte) 0);
                }
                Arrays.fill(plaintextPassword, '\0');
            }
        } else {
            accountDao.updateAccount(account);
        }
    }

    public void deleteAccount(int id) {
        checkUnlocked();
        accountDao.deleteAccount(id);
    }

    public List<Account> search(String query, int limit, int offset) {
        return accountDao.search(query, limit, offset);
    }

    private void checkUnlocked() {
        if (!vaultService.isUnlocked()) {
            throw new RuntimeException("Vault is locked. Unlock first.");
        }
    }

    private EncryptedData encryptPassword(char[] plaintextPassword) {
        checkUnlocked();
        SecretKey key = vaultService.getSessionKeyForServices();
        byte[] iv = PasswordEncryptor.generateIV();
        byte[] plaintextBytes = null;
        try {
            plaintextBytes = new String(plaintextPassword).getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = PasswordEncryptor.encrypt(plaintextBytes, key, iv);
            return new EncryptedData(ciphertext, iv);
        } finally {
            if (plaintextBytes != null) Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    private char[] decryptPasswordBytesToCharArray(byte[] ciphertext, byte[] iv) {
        checkUnlocked();
        SecretKey key = vaultService.getSessionKeyForServices();
        byte[] plaintextBytes = PasswordEncryptor.decrypt(ciphertext, key, iv);
        try {
            String s = new String(plaintextBytes, StandardCharsets.UTF_8);
            char[] chars = s.toCharArray();
            return chars;
        } finally {
            if (plaintextBytes != null) Arrays.fill(plaintextBytes, (byte) 0);
        }
    }

    private static class EncryptedData {
        final byte[] ciphertext;
        final byte[] iv;
        EncryptedData(byte[] ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }
    }
}
