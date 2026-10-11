# DOOF Vault — System Design & Architecture

Design of the hardened password manager. Phases 1 (hardening) and 2 (terminal UI) are implemented. See `SECURITY.md` for the threat model and known limitations and `TUI_DESIGN.md` for the UI.

## 1. Goals
- Local, single-user password vault with strong, modern crypto.
- Keyboard-driven terminal UI (TUI) that works on a local desktop, over SSH, and on a headless console.
- Portable: the whole vault is one encrypted SQLite file.
- Portfolio piece demonstrating security-aware design for a sysadmin/security career.

## 2. Non-goals
- Multi-user / shared vaults, cloud sync, browser autofill.
- Migrating vaults from the old MySQL version (fresh start).
- OSC 52 clipboard over SSH (possible future enhancement).

## 3. Tech stack
| Concern | Choice | Notes |
|---|---|---|
| Language | Java 17+ | |
| Build | Maven | Runnable fat JAR (`maven-shade-plugin`) |
| Storage | SQLite via `org.xerial:sqlite-jdbc` | Replaces MySQL |
| KDF | Argon2id via Bouncy Castle (`bcprov`) | Pure Java, no native libs |
| Key split | HKDF-SHA256 (Bouncy Castle) | |
| Cipher | AES-256-GCM (JCA) | 12-byte random IV, 128-bit tag |
| UI | Lanterna 3 (TUI) | Replaced Swing in Phase 2 |
| Clipboard | Java AWT | Only AWT use; probed lazily, unavailable when headless or over SSH without `DISPLAY` |
| Tests | JUnit 5 | |

## 4. Layered architecture
```
┌──────────────────────────────────────────────┐
│ ui.tui    Lanterna screens (Unlock, Create,  │
│           Vault, entry form, change password,│
│           help), widgets, theme, clipboard   │
├──────────────────────────────────────────────┤
│ service   VaultService (create/unlock/lock/  │
│           rotate, idle timer, throttling)    │
│           EntryService (CRUD, reveal, index) │
├──────────────────────────────────────────────┤
│ crypto    Kdf + KdfParams (Argon2id),        │
│           KeyHierarchy (HKDF), AesGcm,       │
│           SecureWipe                         │
├──────────────────────────────────────────────┤
│ dao       VaultMetadataDao, EntryDao,        │
│           VaultRotationDao (JDBC)            │
├──────────────────────────────────────────────┤
│ db        ConnectionManager (SQLite file,    │
│           path + permission checks)          │
└──────────────────────────────────────────────┘
```
Rules:
- UI talks only to services. Services own all crypto. DAOs store/load opaque bytes only and never see plaintext.
- UI talks to services through `ApplicationConnector` only; it holds site/user summaries and gets secrets as `char[]` through callbacks (`withPassword`, `withNotes`) that wipe them afterwards.
- Package root: `com.doof.passwordmanager` (`ui.tui`, `app` (wiring), `service`, `crypto`, `dao`, `db`, `model`, `util`). Entry point: `ui.tui.TuiApp`.

## 5. Cryptographic design
```
master password ──Argon2id(salt, params)──► master key (32 B, memory only)
                                             ├─ HKDF(info="doof-auth") ──► auth key ──► stored in auth_hash
                                             └─ HKDF(info="doof-enc")  ──► AES-256 key (memory only)
```
- **Argon2id params (default):** memory 64 MiB, iterations 3, parallelism 1, salt 16 B, output 32 B. Stored per vault in `kdf_params` (JSON, bounds-checked on read) so they can be raised later; rotation re-derives with the current defaults.
- **Unlock:** recompute auth key, constant-time compare with `auth_hash`. HKDF without salt (the master key is already salted). Encryption key never written to disk.
- **Entry encryption:** all fields (site, username, password, notes) serialized as one JSON object and encrypted with AES-256-GCM, fresh random 12-byte IV per encryption. AAD = `"doof-entry"` + 8-byte big-endian entry `id`, to stop ciphertext swapping between rows. Ids come from `AUTOINCREMENT` so they are never reused. New entries get their id first, then are encrypted, in one transaction.
- **Wiping:** `char[]`/`byte[]` for secrets, zeroed after use. Master key wiped right after the HKDF split; enc key held only by `VaultService` and wiped on lock. (The JCA's internal key copies can't be zeroed; see `SECURITY.md`.)
- **Versioning:** `enc_version` (currently 1) on metadata and entries, and the schema version in `PRAGMA user_version` (currently 2), to allow future changes. Unknown versions are refused.

## 6. Data model (SQLite)
```sql
CREATE TABLE vault_metadata (
  id           INTEGER PRIMARY KEY CHECK (id = 1),  -- single vault
  kdf_salt     BLOB    NOT NULL,
  kdf_params   TEXT    NOT NULL,                    -- JSON
  auth_hash    BLOB    NOT NULL,
  enc_version  INTEGER NOT NULL,
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  last_failed_at  INTEGER,                          -- epoch ms
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE entries (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,  -- never reused: id is the AES-GCM AAD
  data_enc     BLOB    NOT NULL,   -- AES-GCM(JSON {site,user,password,notes})
  iv           BLOB    NOT NULL,
  enc_version  INTEGER NOT NULL,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);
```
- Schema is a resource (`src/main/resources/db/schema.sql`) applied once when a vault file is created.
- All entry fields are encrypted together; the DB reveals only entry count, timestamps and ciphertext lengths.
- After unlock an in-memory index holds only site/user (the UI lists, sorts and searches it); it is dropped on lock. Passwords and notes are decrypted only on reveal/copy/edit.

## 7. Storage & file security
- Default path: `~/.doof/vault.db` (override with `DOOF_VAULT_PATH` env var or `--vault <path>`).
- Directory `700`, file `600` on create (the app creates the file before SQLite opens it). On open, warn if group/other have any access to the file or its directory (POSIX systems).
- `PRAGMA secure_delete = ON` so deleted entries are overwritten inside the file.
- No credentials in code or config: SQLite has no DB users.

## 8. Session & runtime behavior
- **Auto-lock:** 5 min idle. Every keypress calls `resetSessionTimer`; `VaultService` tracks the last activity on its injectable clock and reports the time left (header countdown, `[LOCKING]` in the last 30s). The UI loop locks at 0; a real-time scheduled lock in the service is a safety net. Lock (idle or `l`) clears our clipboard value, wipes keys and open forms, blanks the terminal and returns to Unlock.
- **Throttling:** after the n-th consecutive failed unlock, the next attempt waits min(1s·2^(n-1), 30s); counter persisted in `vault_metadata` (written before the error is reported) so restarting doesn't reset it. Attempts during the wait are refused before the KDF runs and not counted. Reset on success. Enforced in `VaultService`, not the UI. Does not protect a copied file (see §10).
- **Clipboard:** `c` copies the password, `u` the username, via the Java AWT clipboard when a desktop session exists. A copied password is cleared after 20s, on lock and on quit, only if the clipboard still holds it (SHA-256 compare). If unavailable (headless, or SSH without `DISPLAY`), the status bar says `clipboard unavailable - press [s] to reveal`.
- **Reveal:** `s` (password) and `n` (notes) show the value for 10s then re-mask; moving, searching, help, lock and quit re-mask at once. Works everywhere.
- **Paste:** typing/pasting into fields works everywhere (terminal sends keystrokes).
- **Slow work:** Argon2id (unlock, create, rotation) runs on a worker thread; the UI shows a spinner and picks up the result on its own thread.
- **Rotation:** change master password → verify old password (same throttling as unlock), new salt, new keys with current default params, re-encrypt all entries (same ids/AAD) and replace metadata in one SQLite transaction; rollback on any failure. Session continues with the new key.

## 9. TUI design
Full spec with wireframes, key bindings and UX rules: [`TUI_DESIGN.md`](TUI_DESIGN.md).

Summary: Lanterna, keyboard-only, min 80x24, content capped at 120 columns and centered, purple palette (bg `#0d0b14`, text `#e0d7f5`, primary `#a277ff`, dim `#6b5b95`, success `#61ffca`, error `#ff6188`), `NO_COLOR` and `--ascii` fallbacks, no emojis. Screens: Unlock, Create, Vault (list + search + details), Entry modal (with generator), Change password, Help. Idle auto-lock resets on every keypress; lock and quit clear the screen.

## 10. Threat model (summary)
| Threat | Mitigation |
|---|---|
| Stolen vault file | All entry data AES-GCM encrypted; Argon2id makes offline guessing expensive |
| Online guessing at unlock | Persistent exponential throttling |
| Offline guessing on a copied file | Argon2id cost + strong master password only (throttling does not apply) |
| Failed master password change | Single transaction; old password keeps working |
| Ciphertext tampering / row swap | GCM auth tag + entry id as AAD |
| Unattended unlocked session | Auto-lock + key wiping |
| Clipboard snooping | 20s auto-clear; reveal-only fallback |
| Secrets in source/git | No credentials; vault file outside repo, `.gitignore`d |
| **Out of scope** | Compromised host (keylogger, memory dump, root attacker) |

## 11. Roadmap
- **Phase 1 – Hardening (done):** Maven → SQLite → crypto → encrypted fields → on-demand decrypt → throttling → rotation → SECURITY.md/README.
- **Phase 2 – TUI (done):** Lanterna UI per §9 / `TUI_DESIGN.md`, idle auto-lock, clipboard auto-clear, generator, change master password; Swing and the console harness removed.
- **Phase 3 – Deployment:** homelab deploy, encrypted backups, file-ownership hardening.
