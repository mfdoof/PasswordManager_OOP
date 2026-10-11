package com.doof.passwordmanager.service;

import com.doof.passwordmanager.crypto.AesGcm;
import com.doof.passwordmanager.crypto.SecureWipe;
import com.doof.passwordmanager.dao.EntryDao;
import com.doof.passwordmanager.model.Entry;
import com.doof.passwordmanager.util.InputValidator;
import com.doof.passwordmanager.util.ValidationException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Entry CRUD on top of {@link EntryDao}. Every field is encrypted together into one blob,
 * with the entry id bound as AES-GCM AAD so a ciphertext moved to another row fails to decrypt.
 * <p>
 * Decrypt on demand: after unlock only site + user are kept in memory (the index), which
 * serves listing, search and duplicate checks. Passwords and notes are decrypted only when
 * asked for ({@link #withPassword}, {@link #revealNotes}); passwords are wiped right after use.
 * The index is dropped when the vault locks.
 */
public class EntryService {

    /** What listing and search return. Deliberately has no password or notes. */
    public static final class EntrySummary {
        private final long id;
        private final String site;
        private final String user;
        private final Instant createdAt;
        private final Instant updatedAt;

        EntrySummary(long id, String site, String user, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.site = site;
            this.user = user;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public String getSite() { return site; }
        public String getUser() { return user; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        @Override
        public String toString() {
            return "EntrySummary{id=" + id + ", site='" + site + "', user='" + user + "'}";
        }
    }

    private final EntryDao entryDao;
    private final VaultService vaultService;
    private TreeMap<Long, EntrySummary> index;  // guarded by this; null when not loaded

    public EntryService(EntryDao entryDao, VaultService vaultService) {
        this.entryDao = entryDao;
        this.vaultService = vaultService;
        vaultService.addLockListener(this::clearIndex);
    }

    /** (Re)loads the site/user index. Called after unlock; other methods load it lazily. */
    public synchronized void loadIndex() {
        checkUnlocked();
        TreeMap<Long, EntrySummary> fresh = new TreeMap<>();
        for (Entry stored : entryDao.findAll()) {
            fresh.put(stored.getId(), summarize(stored));
        }
        index = fresh;
    }

    public synchronized List<EntrySummary> listEntries() {
        return new ArrayList<>(index().values());
    }

    public synchronized EntrySummary getEntry(long id) {
        return index().get(id);
    }

    /** Case-insensitive substring match on site or user, in id order. */
    public synchronized List<EntrySummary> search(String query, int limit, int offset) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<EntrySummary> matches = new ArrayList<>();
        int skipped = 0;
        for (EntrySummary e : index().values()) {
            boolean match = e.site.toLowerCase(Locale.ROOT).contains(q)
                    || e.user.toLowerCase(Locale.ROOT).contains(q);
            if (match && skipped++ >= offset) {
                if (matches.size() >= limit) break;
                matches.add(e);
            }
        }
        return matches;
    }

    /**
     * Decrypts the password of entry {@code id}, passes it to {@code use}, and wipes it as
     * soon as {@code use} returns. {@code use} must not keep the array.
     */
    public <T> T withPassword(long id, Function<char[], T> use) {
        Objects.requireNonNull(use);
        char[] password = decrypt(require(id), EntryCodec::decodePassword);
        try {
            return use.apply(password);
        } finally {
            SecureWipe.wipe(password);
        }
    }

    /** Decrypts the notes of entry {@code id} on demand; null if none. */
    public String revealNotes(long id) {
        return decrypt(require(id), EntryCodec::decodeNotes);
    }

    /** Adds an entry. Wipes {@code password}. {@code notes} may be null. */
    public synchronized long addEntry(String site, String user, char[] password, String notes) {
        Objects.requireNonNull(site, "site is required");
        Objects.requireNonNull(user, "user is required");
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("password is required");
        }

        try {
            site = InputValidator.sanitize(site);
            user = InputValidator.sanitize(user);

            InputValidator.validateUsername(user);
            InputValidator.validateWebsite(site);
            InputValidator.validatePassword(password);

            checkUnlocked();
            checkDuplicate(site, user, null);

            String s = site, u = user;
            Entry stored = entryDao.insert(id -> seal(id, s, u, password, notes));
            index().put(stored.getId(), toSummary(stored, s, u));
            return stored.getId();
        } finally {
            SecureWipe.wipe(password);
        }
    }

    /**
     * Updates an entry. A null or empty {@code newPassword} keeps the current password;
     * null {@code notes} keeps the current notes. The entry is re-encrypted with a fresh IV.
     * Wipes {@code newPassword}.
     */
    public synchronized void updateEntry(long id, String site, String user, char[] newPassword, String notes) {
        char[] password = null;
        try {
            checkUnlocked();

            site = InputValidator.sanitize(site);
            user = InputValidator.sanitize(user);
            InputValidator.validateUsername(user);
            InputValidator.validateWebsite(site);

            EntrySummary current = index().get(id);
            Entry existing = entryDao.findById(id);
            if (current == null || existing == null) {
                throw new RuntimeException("Entry not found: " + id);
            }

            boolean changedSiteOrUser = !current.user.equalsIgnoreCase(user)
                    || !current.site.equalsIgnoreCase(site);
            if (changedSiteOrUser) {
                checkDuplicate(site, user, id);
            }

            if (newPassword != null && newPassword.length > 0) {
                InputValidator.validatePassword(newPassword);
                password = newPassword.clone();
            } else {
                password = decrypt(existing, EntryCodec::decodePassword);
            }
            String keptNotes = notes != null ? notes : decrypt(existing, EntryCodec::decodeNotes);

            Entry sealed = seal(id, site, user, password, keptNotes);
            sealed.setId(id);
            entryDao.update(sealed);
            index().put(id, new EntrySummary(id, site, user, current.createdAt, Instant.ofEpochMilli(sealed.getUpdatedAt())));
        } finally {
            SecureWipe.wipe(password);
            SecureWipe.wipe(newPassword);
        }
    }

    public synchronized void deleteEntry(long id) {
        checkUnlocked();
        entryDao.delete(id);
        if (index != null) index.remove(id);
    }

    private synchronized void clearIndex() {
        index = null;
    }

    private TreeMap<Long, EntrySummary> index() {
        checkUnlocked();
        if (index == null) {
            loadIndex();
        }
        return index;
    }

    private Entry require(long id) {
        checkUnlocked();
        Entry stored = entryDao.findById(id);
        if (stored == null) {
            throw new RuntimeException("Entry not found: " + id);
        }
        return stored;
    }

    private void checkDuplicate(String site, String user, Long ignoreId) {
        for (EntrySummary e : index().values()) {
            boolean same = e.user.equalsIgnoreCase(user) && e.site.equalsIgnoreCase(site);
            if (same && (ignoreId == null || e.id != ignoreId)) {
                throw new ValidationException("An entry with this site and user already exists.");
            }
        }
    }

    private void checkUnlocked() {
        if (!vaultService.isUnlocked()) {
            throw new RuntimeException("Vault is locked. Unlock first.");
        }
    }

    private EntrySummary summarize(Entry stored) {
        EntryCodec.Summary s = decrypt(stored, EntryCodec::decodeSummary);
        return toSummary(stored, s.site, s.user);
    }

    private static EntrySummary toSummary(Entry stored, String site, String user) {
        return new EntrySummary(stored.getId(), site, user,
                Instant.ofEpochMilli(stored.getCreatedAt()), Instant.ofEpochMilli(stored.getUpdatedAt()));
    }

    /** Encrypts the fields for entry {@code id}; returns an Entry holding blob, IV and enc version. */
    private Entry seal(long id, String site, String user, char[] password, String notes) {
        byte[] plaintext = EntryCodec.encode(site, user, password, notes);
        try {
            AesGcm.Sealed sealed = vaultService.withEncKey(key -> AesGcm.encrypt(key, plaintext, aad(id)));
            return new Entry(sealed.ciphertext, sealed.iv, VaultService.ENC_VERSION);
        } finally {
            SecureWipe.wipe(plaintext);
        }
    }

    /** Decrypts {@code stored} and extracts only what {@code extract} needs; the plaintext is wiped. */
    private <T> T decrypt(Entry stored, Function<byte[], T> extract) {
        byte[] plaintext = vaultService.withEncKey(key ->
                AesGcm.decrypt(key, stored.getIv(), stored.getDataEnc(), aad(stored.getId())));
        try {
            return extract.apply(plaintext);
        } finally {
            SecureWipe.wipe(plaintext);
        }
    }

    /** AAD = "doof-entry" + 8-byte big-endian entry id. */
    static byte[] aad(long id) {
        byte[] tag = "doof-entry".getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(tag.length + Long.BYTES).put(tag).putLong(id).array();
    }
}
