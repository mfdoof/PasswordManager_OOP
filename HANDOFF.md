# Handoff — DOOF Vault (PasswordManager_OOP)

Read this first, then `docs/ARCHITECTURE.md` (the target design).

## Rules for Claude
- **Do NOT add Claude as a contributor or co-author anywhere**: no `Co-Authored-By` lines, no "Generated with Claude" footers in commits/PRs, no mention in README/LICENSE/authors.
- **Do not run git commands that change history or remotes** (commit, push, rebase, reset, branch). The owner (Mave) handles git. Just say when a chunk is ready to commit and suggest a commit message.
- Keep replies brief and direct. Don't dump many commands at once.
- Work in small, commit-sized steps (one roadmap step at a time), then stop for review.

## Project context
- Old university OOP project: Java + Swing + MySQL password manager, being reworked as a portfolio piece for a sysadmin/security career.
- Owner: Mave (GitHub `mfdoof`). Repo: `git@github.com:mfdoof/PasswordManager_OOP.git`.

## Current state (as of 2026-10-11)
- Code in `src/main/java/com/doof/passwordmanager/`, built with Maven (Java 17, fat JAR via shade, main class `app.Main`). Storage is SQLite (`~/.doof/vault.db`, or `DOOF_VAULT_PATH`, or `--vault <path>`); schema in `src/main/resources/db/schema.sql`. UI is still Swing. Crypto (`crypto` package): Argon2id → HKDF auth/enc keys → AES-256-GCM with entry id as AAD.
- Existing layers: `model`, `dao` (JDBC), `service`, `util` (PasswordHasher = PBKDF2, PasswordEncryptor = AES-GCM, InputValidator), `db/ConnectionManager`, `app` (Swing `Main`, `Dashboard`; also `ConsolePasswordManager`).

## Known issues in the original
1. ~~Hardcoded DB credentials `appuser/appuser`~~ (removed Step 2; still in git history, harmless since MySQL is gone).
2. ~~Auth hash and encryption key derived from same PBKDF2 call/salt~~ (Step 3: HKDF split).
3. ~~PBKDF2 at only 100k iterations~~ (Step 3: Argon2id 64 MiB / t=3 / p=1).
4. ~~Email and website stored in plaintext~~ (Step 2: all fields now in one encrypted blob, since the new schema has no plaintext columns).
5. ~~All passwords decrypted into the table model at load~~ (Step 5: only site + user indexed; passwords decrypted on reveal/copy).
6. No unlock throttling.
7. `rotateVault` unimplemented.
8. ~~No build tool~~ (fixed Step 1), ~~no tests~~ (Step 2 added DAO tests), thin README, two entry points.

9. ~~`VaultService` auto-lock thread kept the JVM alive~~ (Step 3: daemon thread).
10. ~~`createVault` wiped the salt referenced by cached metadata~~ (fixed Step 3).
11. JCA copies key bytes internally (`SecretKeySpec`, cipher state); we wipe our own arrays but can't zero those copies. Documented in `AesGcm`.

12. Swing limits (accepted, Swing goes away in Phase 2): a revealed password becomes a `String` for `JLabel` and for the AWT clipboard, which can't be wiped. The `char[]` copy is wiped on re-mask (10s), refresh or close; clipboard clears at 20s. `user` is still validated as an email (old app rule); revisit for the TUI.

## Decisions made
| Topic | Decision |
|---|---|
| Order | Harden → TUI → Deployment |
| Storage | SQLite (drop MySQL) |
| KDF | Argon2id via Bouncy Castle + HKDF key split |
| Build | Maven, fat JAR |
| UI | Real TUI with Lanterna, terminal look, purple palette |
| Clipboard | Java AWT clipboard only; no OSC 52. Reveal (`s`) works local/SSH/headless; paste works everywhere |
| Old vaults | No migration; fresh start |

## Next steps (Phase 1)
1. [x] Maven setup: `pom.xml`, move to `src/main/java`, deps (`sqlite-jdbc`, `bcprov`, JUnit 5), shade plugin; remove `.idea`, `*.iml`, MySQL lib; update `.gitignore` (`target/`, `*.db`).
2. [x] SQLite migration: new schema (ARCHITECTURE §6), `ConnectionManager` with path + perms checks, rewrite DAOs.
3. [x] Crypto: Argon2id + HKDF, AAD, tests (round-trip, wrong password, tampered ciphertext).
4. [x] Encrypt all entry fields; in-memory search.
5. [x] Decrypt on demand + wipe.
6. [ ] Persistent unlock throttling.
7. [ ] Master password rotation (single transaction).
8. [ ] `SECURITY.md` + README rewrite.

Then Phase 2 (TUI) and Phase 3 (deployment) per ARCHITECTURE §11.

## Progress log
- 2026-10-11: Reviewed codebase, agreed on design, wrote ARCHITECTURE.md and this handoff. No code changes yet.
- 2026-10-11: Step 1 done. Added `pom.xml` (Java 17; sqlite-jdbc, bcprov-jdk18on, JUnit 5; shade plugin with Main-Class `com.doof.passwordmanager.app.Main`). `git mv src/com` to `src/main/java/com`. Removed `.idea/` and `Password_Manager.iml`; MySQL connector dropped (loaded only via `Class.forName`, so it still compiles). Removed an unused `java.lang.foreign` import (Java 22+ API) from `VaultMetadataDao` that broke the Java 17 build. `.gitignore`: `target/`, `.idea/`, `*.iml`, `*.db*`. `mvn package` passes. `db/schema.sql` is kept until Step 2.
- 2026-10-11: Step 2 done. SQLite via `ConnectionManager.open(path)`: path from `--vault` / `DOOF_VAULT_PATH` / `~/.doof/vault.db`; creates dir 700 + file 600 (file pre-created so SQLite never uses umask perms), warns on group/other access, applies `db/schema.sql` resource once (tracked with `PRAGMA user_version`), enables `secure_delete`. MySQL credentials removed. `Account`→`Entry` (opaque `data_enc` + `iv`), `AccountDao`→`EntryDao`, both DAOs rewritten (epoch-ms timestamps via injectable `Clock`). `AccountService` now encrypts a JSON payload `{site,user,password,notes}` (`EntryCodec`) with the existing crypto; search/duplicate checks run in memory. `db/schema.sql` moved to resources. 31 JUnit tests (ConnectionManager, both DAOs, EntryCodec); `mvn package` passes. Smoke test: console app create/add/unlock/list works; vault file contains no plaintext.
- 2026-10-11: Step 3 done. New `crypto` package: `Kdf` (Argon2id via BC; params in `KdfParams`, stored as JSON in `kdf_params`, bounds-checked on read), `KeyHierarchy` (HKDF-SHA256 `doof-auth` / `doof-enc`), `AesGcm` (random 12-byte IV, AAD, `CryptoException` on auth failure), `SecureWipe`. Old `PasswordHasher`/`PasswordEncryptor` replaced (git mv'd to `Kdf`/`AesGcm`). `auth_hash` = HKDF auth key, compared constant-time; master key wiped right after the split; enc key held as `byte[]` only in `VaultService`, accessed via `withEncKey` (same lock as `lockVault`, so auto-lock can't wipe mid-operation) and wiped on lock. AAD = `"doof-entry"` + 8-byte id. `EntryDao.insert` now takes a seal callback: reserves id, encrypts with it, fills row, one transaction. Schema v2: `entries.id` is `AUTOINCREMENT` so ids (= AAD) are never reused (ARCHITECTURE §6 updated); v1 vaults from Step 2 are refused, delete them. `FlatJson` util shared by `EntryCodec` and `KdfParams`. Fixed both logged `VaultService` bugs. 68 tests pass (incl. RFC 5869 HKDF vector, wrong password, tampered ciphertext/IV, swapped rows, params read from `kdf_params`, key wiped on lock). Console smoke test with real params: create/add/wrong-pw/unlock/list OK and JVM exits.
- 2026-10-12: Steps 4 + 5 done. Renamed `AccountService`→`EntryService` (git mv), `AccountView`→`EntrySummary` (id, site, user, timestamps; no password/notes), UI/tests/console renamed too. `EntryService` keeps an in-memory index of site + user only (loaded on unlock via `ApplicationConnector.unlockVault`, lazily otherwise, dropped on lock via new `VaultService.addLockListener`); list/search/duplicate checks use it without touching the DB. Passwords via `withPassword(id, fn)` (decrypt → `char[]` → callback → wipe, also on exception); notes via `revealNotes(id)`; `addEntry`/`updateEntry` take notes (null on update = keep). `FlatJson` is now `char[]`-based with per-key modes (VALUE/SECRET/SKIP), so the listing path never materializes the password and the reveal path gets it as a wipeable `char[]`; all scratch buffers wiped. `EntryCodec` split into `decodeSummary`/`decodePassword`/`decodeNotes`. Swing: table holds site/user only; Show decrypts on demand, re-masks after 10s, clipboard clears after 20s; raw NUL bytes in `Dashboard.java` replaced by `'\0'` (file is plain text again). Console harness: list masks passwords, option 7 reveals. 87 tests pass; smoke test OK.
