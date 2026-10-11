package com.doof.passwordmanager.service;

import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.util.InputValidator;
import com.doof.passwordmanager.util.PasswordEncryptor;
import com.doof.passwordmanager.util.ValidationException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Account CRUD on top of {@link EntryDao}. Every field is encrypted together into one
 * blob, so lookups, search and duplicate checks run in memory after decryption.
 */
public class AccountService {
    public static class AccountView {
        private final long id;
        private final String email;
        private final String website;
        private final char[] password;
        private final Instant createdAt;
        private final Instant updatedAt;

        public AccountView(long id, String email, String website, char[] password, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.email = email;
            this.website = website;
            this.password = password;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public String getEmail() { return email; }
        public String getWebsite() { return website; }
        public char[] getPassword() { return password; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
    }

    private final EntryDao entryDao;
    private final VaultService vaultService;

    public AccountService(EntryDao entryDao, VaultService vaultService) {
        this.entryDao = entryDao;
        this.vaultService = vaultService;
    }

    public List<AccountView> getAllAccountsDecrypted() {
        checkUnlocked();
        List<Entry> rows = entryDao.findAll();
        List<AccountView> result = new ArrayList<>(rows.size());
        for (Entry stored : rows) {
            result.add(toView(stored));
        }
        return result;
    }

    public AccountView getAccountByIdDecrypted(long id) {
        checkUnlocked();
        Entry stored = entryDao.findById(id);
        return stored == null ? null : toView(stored);
    }

    public long addAccount(String email, String website, char[] plaintextPassword) {
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(website, "website is required");
        if (plaintextPassword == null || plaintextPassword.length == 0) {
            throw new IllegalArgumentException("plaintextPassword is required");
        }

        try {
            email = InputValidator.sanitize(email);
            website = InputValidator.sanitize(website);

            InputValidator.validateEmail(email);
            InputValidator.validateWebsite(website);
            InputValidator.validatePassword(plaintextPassword);

            checkUnlocked();
            checkDuplicate(email, website, null);

            Entry entry = new Entry();
            encryptInto(entry, new EntryCodec.Fields(website, email, new String(plaintextPassword), null));
            entryDao.insert(entry);
            return entry.getId();
        } finally {
            Arrays.fill(plaintextPassword, '\0');
        }
    }

    /**
     * Updates an account. A null or empty {@code plaintextPassword} keeps the current password.
     * The whole entry is re-encrypted with a fresh IV either way.
     */
    public void updateAccount(long id, String email, String website, char[] plaintextPassword) {
        try {
            checkUnlocked();

            email = InputValidator.sanitize(email);
            website = InputValidator.sanitize(website);
            InputValidator.validateEmail(email);
            InputValidator.validateWebsite(website);

            Entry existing = entryDao.findById(id);
            if (existing == null) {
                throw new RuntimeException("Account not found: " + id);
            }
            EntryCodec.Fields current = decryptFields(existing);

            boolean changedEmailOrWebsite = !current.user.equalsIgnoreCase(email)
                    || !current.site.equalsIgnoreCase(website);
            if (changedEmailOrWebsite) {
                checkDuplicate(email, website, id);
            }

            String password = current.password;
            if (plaintextPassword != null && plaintextPassword.length > 0) {
                InputValidator.validatePassword(plaintextPassword);
                password = new String(plaintextPassword);
            }

            encryptInto(existing, new EntryCodec.Fields(website, email, password, current.notes));
            entryDao.update(existing);
        } finally {
            if (plaintextPassword != null) Arrays.fill(plaintextPassword, '\0');
        }
    }

    public void deleteAccount(long id) {
        checkUnlocked();
        entryDao.delete(id);
    }

    /** Case-insensitive substring match on email or website, in id order. */
    public List<AccountView> search(String query, int limit, int offset) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AccountView> matches = new ArrayList<>();
        int skipped = 0;
        for (AccountView v : getAllAccountsDecrypted()) {
            boolean match = v.getEmail().toLowerCase(Locale.ROOT).contains(q)
                    || v.getWebsite().toLowerCase(Locale.ROOT).contains(q);
            if (match && skipped++ >= offset && matches.size() < limit) {
                matches.add(v);
            } else {
                Arrays.fill(v.getPassword(), '\0');
            }
        }
        return matches;
    }

    private void checkDuplicate(String email, String website, Long ignoreId) {
        for (AccountView v : getAllAccountsDecrypted()) {
            Arrays.fill(v.getPassword(), '\0');
            boolean same = v.getEmail().equalsIgnoreCase(email) && v.getWebsite().equalsIgnoreCase(website);
            if (same && (ignoreId == null || v.getId() != ignoreId)) {
                throw new ValidationException("An account with this email and website already exists.");
            }
        }
    }

    private void checkUnlocked() {
        if (!vaultService.isUnlocked()) {
            throw new RuntimeException("Vault is locked. Unlock first.");
        }
    }

    private AccountView toView(Entry stored) {
        EntryCodec.Fields f = decryptFields(stored);
        return new AccountView(
                stored.getId(),
                f.user,
                f.site,
                f.password == null ? new char[0] : f.password.toCharArray(),
                Instant.ofEpochMilli(stored.getCreatedAt()),
                Instant.ofEpochMilli(stored.getUpdatedAt())
        );
    }

    private void encryptInto(Entry entry, EntryCodec.Fields fields) {
        checkUnlocked();
        SecretKey key = vaultService.getSessionKeyForServices();
        byte[] iv = PasswordEncryptor.generateIV();
        // PasswordEncryptor.encrypt wipes the plaintext bytes.
        byte[] ciphertext = PasswordEncryptor.encrypt(EntryCodec.encode(fields), key, iv);
        entry.setDataEnc(ciphertext);
        entry.setIv(iv);
        entry.setEncVersion(vaultService.getMetadata().getEncVersion());
    }

    private EntryCodec.Fields decryptFields(Entry stored) {
        checkUnlocked();
        SecretKey key = vaultService.getSessionKeyForServices();
        byte[] plaintextBytes = PasswordEncryptor.decrypt(stored.getDataEnc(), key, stored.getIv());
        try {
            return EntryCodec.decode(plaintextBytes);
        } finally {
            Arrays.fill(plaintextBytes, (byte) 0);
        }
    }
}
