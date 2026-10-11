# Security

DOOF Vault is a local, single-user password manager. This document describes what it protects against, how, and where its limits are. The full design is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub: **Security → Report a vulnerability** on [mfdoof/PasswordManager_OOP](https://github.com/mfdoof/PasswordManager_OOP/security/advisories/new). If that is not available, open an issue asking for a contact, without technical details.

## Threat model

**Protected**

| Threat | Mitigation |
|---|---|
| Stolen or copied `vault.db` | Every entry field is AES-256-GCM encrypted; the key comes from Argon2id, so each password guess is expensive |
| Guessing through the app | Persistent exponential unlock delay (see [Throttling](#throttling)) |
| Tampering with or swapping ciphertext between rows | GCM authentication tag + entry id bound as AAD |
| Other local users reading the file | Directory `700`, file `600`; warning on open if group/other have access |
| Unattended unlocked session | Auto-lock wipes the encryption key |
| Secrets in source or git | No credentials anywhere; SQLite has no DB users; `*.db` is git-ignored |
| Failed master password change | Rotation is one SQLite transaction; any failure leaves the old password working |

**Not protected (out of scope)**

- **A compromised host.** Keyloggers, malware, a root user, or anyone who can read process memory or attach a debugger can capture the master password or keys while the vault is unlocked.
- **Weak master passwords.** Argon2id slows guessing but cannot save a password that is in a wordlist. Creating or changing the master password requires 12+ characters with upper case, lower case and a digit (enforced in `VaultService`); the strength bar in the UI is a rough estimate, not a guarantee.
- **Metadata.** The file shows how many entries exist, when they were created/updated, and roughly how long each entry's contents are (ciphertext length).
- **Storage remnants.** SQLite `secure_delete` zeroes freed pages, but SSD wear-levelling, filesystem snapshots, backups and old journal files are outside the app's control.

## Cryptographic design

```
master password ──Argon2id(salt, params)──► master key (32 B)
                                             ├─ HKDF-SHA256 "doof-auth" ──► auth key  (stored)
                                             └─ HKDF-SHA256 "doof-enc"  ──► enc key   (memory only)
```

- **KDF:** Argon2id (Bouncy Castle), 64 MiB memory, 3 iterations, parallelism 1, 16-byte random salt, 32-byte output. Parameters are stored per vault as JSON in `kdf_params` and bounds-checked on read, so they can be raised later without breaking old vaults.
- **Key split:** HKDF derives two independent keys, so the stored auth key reveals nothing about the encryption key. The master key is wiped right after the split.
- **Unlock:** the auth key is recomputed and compared in constant time (`MessageDigest.isEqual`).
- **Entries:** site, username, password and notes are serialized together and encrypted as one AES-256-GCM blob with a fresh random 12-byte IV and a 128-bit tag. The AAD is `"doof-entry"` plus the 8-byte entry id, so a ciphertext copied into another row fails to decrypt.
- **Why `AUTOINCREMENT`:** SQLite normally reuses the id of a deleted last row. Because the id is the AAD, a reused id would let an old ciphertext (e.g. from a backup) validate in a new row. `AUTOINCREMENT` guarantees ids are never reused.
- **Versioning:** `enc_version` on metadata and entries, plus a schema version (`PRAGMA user_version`), allow future algorithm changes. Vaults with an unknown version are refused.

## Throttling

After the *n*-th consecutive wrong password, the next attempt must wait `min(1s × 2^(n-1), 30s)`. The counter and timestamp are stored in `vault_metadata`, so restarting the app does not reset them. Attempts during the wait are refused before the KDF runs and are not counted, even with the right password. A failure is written to disk before the error is shown. A successful unlock resets the counter. Changing the master password goes through the same check.

> **Throttling does not stop offline attacks.** Someone with a copy of `vault.db` ignores the app and its counter and guesses directly. Against that, the only defense is Argon2id's cost combined with a strong master password. Use a long passphrase.

## Runtime behavior

- **Decrypt on demand:** after unlock only site + username are kept in memory for listing and search. A password or the notes are decrypted only when revealed or copied, into a `char[]` that is wiped as soon as it has been used (for a reveal: when it re-masks). Moving the selection, searching, opening help, locking, auto-lock and quitting all re-mask immediately.
- **Wiping:** secrets live in `char[]`/`byte[]` and are zeroed after use; the encryption key is zeroed on lock.
- **Auto-lock:** the session locks 5 minutes after unlock and the key is wiped. The current Swing UI does not extend this on activity, and the dashboard window stays open but every action is refused until you unlock again. The Phase 2 TUI adds idle-based reset and returns to the unlock screen.
- **Clipboard:** copying uses the Java AWT clipboard. A copied password is cleared after 20 seconds, and also on lock and on quit, but only if the clipboard still holds it (checked by SHA-256, so the app does not keep the value). A copied username is not cleared. Over SSH without X forwarding, or headless, the clipboard is reported as unavailable instead of silently copying to the remote machine's clipboard; there is no OSC 52 support. Reveal works everywhere. Other apps can read the clipboard while it holds the password.
- **Reveal:** `s` shows the password and `n` the notes in the details pane for 10 seconds with a countdown, then re-masks. Characters are drawn cell by cell, so the app never builds a `String` of the value for display.
- **Master password rotation:** verifies the old password, generates a new salt and keys using the current default Argon2id parameters (so rotation also upgrades old parameters), and re-encrypts every entry and replaces the metadata in **one** SQLite transaction. Any failure rolls back everything; the vault still opens with the old password.

## File security

- Default location `~/.doof/vault.db`; override with `--vault <path>` or `DOOF_VAULT_PATH`.
- On creation: directory `700`, file `600`. The file is created by the app before SQLite opens it, so it never gets umask-default permissions.
- On every open (POSIX systems): a warning if the file or its directory is accessible by group or others.
- `PRAGMA secure_delete = ON` overwrites deleted data inside the database file.

## Known limitations

- **JVM key copies.** The JCA (`SecretKeySpec`, cipher state) copies key bytes internally. The app wipes its own arrays but cannot zero those copies; they remain until garbage-collected.
- **Clipboard `String`s.** The AWT clipboard API only accepts a `java.lang.String`, which is immutable and cannot be wiped. Copying a password therefore leaves one copy in JVM memory until garbage collection, and reading the clipboard back for the clear-if-unchanged check makes another. The operating system's clipboard also holds the value until it is cleared.
- **Notes on save.** The entry form keeps notes in a `char[]`, but the service API takes them as a `String` when saving, so one unwipeable copy exists until garbage collection. Passwords stay `char[]` all the way to encryption.
- **Entry passwords are not policy-checked.** Stored passwords only have to be non-empty (sites have their own rules, some need PINs). The form shows a strength bar and has a generator (SecureRandom, 20 characters by default, at least one character from each enabled set); the strict policy applies to the master password only.
- **Terminal display.** A revealed value is drawn into Lanterna's screen buffers (one object per cell) and sent to the terminal emulator, which keeps its own copy of the screen. The app overwrites the cells on re-mask but cannot wipe the emulator's memory or a session recording.
- **Swing `String`s (until Phase 2 step 7).** The old Swing UI, still reachable with `--swing`, puts revealed passwords into `String`s for its labels. It is removed at the end of Phase 2.
- **Session timeout is fixed, not idle-based** in the Swing UI (see Auto-lock).
- **Write access to the file.** Someone who can modify `vault.db` can reset the failure counter or corrupt data (denial of service). That gains them nothing beyond what an offline attack on a copy already allows; changing `kdf_params` or the salt just makes unlock fail.
