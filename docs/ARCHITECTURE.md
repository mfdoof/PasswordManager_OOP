# DOOF Vault — System Design & Architecture

Target design for the hardened password manager. Describes where the project is going; see `HANDOFF.md` for current status.

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
| UI | Lanterna (TUI) | Replaces Swing |
| Tests | JUnit 5 | |

## 4. Layered architecture
```
┌──────────────────────────────────────────────┐
│ ui        Lanterna screens (Unlock, Vault,     │
│           Entry modal), keybindings, theme     │
├──────────────────────────────────────────────┤
│ service   VaultService (create/unlock/lock/    │
│           rotate, auto-lock, throttling)       │
│           EntryService (CRUD, search, reveal)  │
├──────────────────────────────────────────────┤
│ crypto    Kdf (Argon2id), KeyHierarchy (HKDF), │
│           Cipher (AES-GCM), SecureWipe         │
├──────────────────────────────────────────────┤
│ dao       VaultMetadataDao, EntryDao (JDBC)    │
├──────────────────────────────────────────────┤
│ db        ConnectionManager (SQLite file,      │
│           path + permission checks)            │
└──────────────────────────────────────────────┘
```
Rules:
- UI talks only to services. Services own all crypto. DAOs store/load opaque bytes only and never see plaintext.
- Package root: `com.doof.passwordmanager` (`ui`, `service`, `crypto`, `dao`, `db`, `model`, `util`).

## 5. Cryptographic design
```
master password ──Argon2id(salt, params)──► master key (32 B, memory only)
                                             ├─ HKDF(info="doof-auth") ──► auth key ──► stored as auth hash
                                             └─ HKDF(info="doof-enc")  ──► AES-256 key (memory only)
```
- **Argon2id params (default):** memory 64 MiB, iterations 3, parallelism 1, salt 16 B, output 32 B. Stored per vault in `kdf_params` (JSON) so they can be raised later.
- **Unlock:** recompute auth key, constant-time compare with stored hash. Encryption key never written to disk.
- **Entry encryption:** each field blob (site, username, password, notes) encrypted with AES-256-GCM, fresh random 12-byte IV per encryption. Entry `id` bound as AAD to stop ciphertext swapping between rows.
- **Wiping:** `char[]`/`byte[]` for secrets, zeroed after use. Master key and enc key wiped on lock.
- **Versioning:** `enc_version` on metadata and entries to allow future algorithm changes.

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
  id           INTEGER PRIMARY KEY,
  data_enc     BLOB    NOT NULL,   -- AES-GCM(JSON {site,user,password,notes})
  iv           BLOB    NOT NULL,
  enc_version  INTEGER NOT NULL,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);
```
- All entry fields are encrypted together; the DB reveals only entry count and timestamps.
- Search runs in memory over decrypted site/user after unlock. Passwords are decrypted only on reveal/copy.

## 7. Storage & file security
- Default path: `~/.doof/vault.db` (override with `DOOF_VAULT_PATH` env var or `--vault <path>`).
- Directory `700`, file `600` on create. On open, warn if group/other have any access (POSIX systems).
- No credentials in code or config: SQLite has no DB users.

## 8. Session & runtime behavior
- **Auto-lock:** 5 min idle (reset on keypress). Lock wipes keys and returns to Unlock screen.
- **Throttling:** after each failed unlock, exponential delay (1s, 2s, 4s… capped 30s); counter persisted in `vault_metadata` so restarting doesn't reset it.
- **Clipboard:** `c` copies via Java AWT clipboard when a desktop session exists; auto-clears after 20s. If unavailable (SSH/headless), status bar says `clipboard unavailable — press [s] to reveal`.
- **Reveal:** `s` shows password for 10s then re-masks. Works everywhere.
- **Paste:** typing/pasting into fields works everywhere (terminal sends keystrokes).
- **Rotation:** change master password → new salt, new keys, re-encrypt all entries in one SQLite transaction; rollback on any failure.

## 9. TUI design
Palette: bg `#0d0b14`, text `#e0d7f5`, primary `#a277ff`, dim `#6b5b95`, success `#61ffca`, error `#ff6188`.

Screens:
1. **Unlock / Create** — ASCII logo, `> master password:` prompt, attempt counter; create mode adds confirm field + strength bar.
2. **Vault** — header (lock state + countdown), `/` search bar, entry list, details pane (masked password), keybinding footer + status messages.
3. **Entry modal** — site/user/password fields, strength bar, generator (length, charsets, `g` regenerate).

Keys: `j/k`/arrows move, `/` search, `a` add, `e` edit, `d` delete (inline `[y/N]` confirm), `s` reveal, `c` copy, `g` generate, `l` lock, `q` quit, `TAB` next field, `ENTER` save, `ESC` cancel.

## 10. Threat model (summary)
| Threat | Mitigation |
|---|---|
| Stolen vault file | All entry data AES-GCM encrypted; Argon2id makes offline guessing expensive |
| Online guessing at unlock | Persistent exponential throttling |
| Ciphertext tampering / row swap | GCM auth tag + entry id as AAD |
| Unattended unlocked session | Auto-lock + key wiping |
| Clipboard snooping | 20s auto-clear; reveal-only fallback |
| Secrets in source/git | No credentials; vault file outside repo, `.gitignore`d |
| **Out of scope** | Compromised host (keylogger, memory dump, root attacker) |

## 11. Roadmap
- **Phase 1 – Hardening:** Maven → SQLite → crypto → encrypted fields → on-demand decrypt → throttling → rotation → SECURITY.md/README.
- **Phase 2 – TUI:** Lanterna UI per §9, remove Swing.
- **Phase 3 – Deployment:** homelab deploy, encrypted backups, file-ownership hardening.
