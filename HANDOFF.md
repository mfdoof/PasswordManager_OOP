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
6. ~~No unlock throttling~~ (Step 6: persisted exponential delay, enforced in `VaultService`).
7. ~~`rotateVault` unimplemented~~ (Step 7).
8. ~~No build tool~~ (fixed Step 1), ~~no tests~~ (Step 2 added DAO tests), thin README, two entry points.

9. ~~`VaultService` auto-lock thread kept the JVM alive~~ (Step 3: daemon thread).
10. ~~`createVault` wiped the salt referenced by cached metadata~~ (fixed Step 3).
11. JCA copies key bytes internally (`SecretKeySpec`, cipher state); we wipe our own arrays but can't zero those copies. Documented in `AesGcm`.

12. Swing limits (accepted, Swing goes away in Phase 2): a revealed password becomes a `String` for `JLabel` and for the AWT clipboard, which can't be wiped. The `char[]` copy is wiped on re-mask (10s), refresh or close; clipboard clears at 20s. ~~`user` validated as an email~~ (Step 6: any non-empty username).

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
6. [x] Persistent unlock throttling.
7. [x] Master password rotation (single transaction).
8. [ ] `SECURITY.md` + README rewrite.
   - SECURITY.md must state: unlock throttling only slows guessing through the app; it does **not** stop offline attacks on a copied `vault.db` (attacker skips the app and its counter). Argon2id (64 MiB, t=3) is the defense there, so a strong master password matters.
   - Also cover: threat model (ARCHITECTURE §10), crypto design (§5), Swing `String` limits (known issue 12), JCA key copies (11), no OSC 52, rotation is all-or-nothing.

Then Phase 2 (TUI) and Phase 3 (deployment) per ARCHITECTURE §11.

## Progress log
- 2026-10-11: Reviewed codebase, agreed on design, wrote ARCHITECTURE.md and this handoff. No code changes yet.
- 2026-10-11: Step 1 done. Added `pom.xml` (Java 17; sqlite-jdbc, bcprov-jdk18on, JUnit 5; shade plugin with Main-Class `com.doof.passwordmanager.app.Main`). `git mv src/com` to `src/main/java/com`. Removed `.idea/` and `Password_Manager.iml`; MySQL connector dropped (loaded only via `Class.forName`, so it still compiles). Removed an unused `java.lang.foreign` import (Java 22+ API) from `VaultMetadataDao` that broke the Java 17 build. `.gitignore`: `target/`, `.idea/`, `*.iml`, `*.db*`. `mvn package` passes. `db/schema.sql` is kept until Step 2.
- 2026-10-11: Step 2 done. SQLite via `ConnectionManager.open(path)`: path from `--vault` / `DOOF_VAULT_PATH` / `~/.doof/vault.db`; creates dir 700 + file 600 (file pre-created so SQLite never uses umask perms), warns on group/other access, applies `db/schema.sql` resource once (tracked with `PRAGMA user_version`), enables `secure_delete`. MySQL credentials removed. `Account`→`Entry` (opaque `data_enc` + `iv`), `AccountDao`→`EntryDao`, both DAOs rewritten (epoch-ms timestamps via injectable `Clock`). `AccountService` now encrypts a JSON payload `{site,user,password,notes}` (`EntryCodec`) with the existing crypto; search/duplicate checks run in memory. `db/schema.sql` moved to resources. 31 JUnit tests (ConnectionManager, both DAOs, EntryCodec); `mvn package` passes. Smoke test: console app create/add/unlock/list works; vault file contains no plaintext.
- 2026-10-11: Step 3 done. New `crypto` package: `Kdf` (Argon2id via BC; params in `KdfParams`, stored as JSON in `kdf_params`, bounds-checked on read), `KeyHierarchy` (HKDF-SHA256 `doof-auth` / `doof-enc`), `AesGcm` (random 12-byte IV, AAD, `CryptoException` on auth failure), `SecureWipe`. Old `PasswordHasher`/`PasswordEncryptor` replaced (git mv'd to `Kdf`/`AesGcm`). `auth_hash` = HKDF auth key, compared constant-time; master key wiped right after the split; enc key held as `byte[]` only in `VaultService`, accessed via `withEncKey` (same lock as `lockVault`, so auto-lock can't wipe mid-operation) and wiped on lock. AAD = `"doof-entry"` + 8-byte id. `EntryDao.insert` now takes a seal callback: reserves id, encrypts with it, fills row, one transaction. Schema v2: `entries.id` is `AUTOINCREMENT` so ids (= AAD) are never reused (ARCHITECTURE §6 updated); v1 vaults from Step 2 are refused, delete them. `FlatJson` util shared by `EntryCodec` and `KdfParams`. Fixed both logged `VaultService` bugs. 68 tests pass (incl. RFC 5869 HKDF vector, wrong password, tampered ciphertext/IV, swapped rows, params read from `kdf_params`, key wiped on lock). Console smoke test with real params: create/add/wrong-pw/unlock/list OK and JVM exits.
- 2026-10-12: Steps 4 + 5 done. Renamed `AccountService`→`EntryService` (git mv), `AccountView`→`EntrySummary` (id, site, user, timestamps; no password/notes), UI/tests/console renamed too. `EntryService` keeps an in-memory index of site + user only (loaded on unlock via `ApplicationConnector.unlockVault`, lazily otherwise, dropped on lock via new `VaultService.addLockListener`); list/search/duplicate checks use it without touching the DB. Passwords via `withPassword(id, fn)` (decrypt → `char[]` → callback → wipe, also on exception); notes via `revealNotes(id)`; `addEntry`/`updateEntry` take notes (null on update = keep). `FlatJson` is now `char[]`-based with per-key modes (VALUE/SECRET/SKIP), so the listing path never materializes the password and the reveal path gets it as a wipeable `char[]`; all scratch buffers wiped. `EntryCodec` split into `decodeSummary`/`decodePassword`/`decodeNotes`. Swing: table holds site/user only; Show decrypts on demand, re-masks after 10s, clipboard clears after 20s; raw NUL bytes in `Dashboard.java` replaced by `'\0'` (file is plain text again). Console harness: list masks passwords, option 7 reveals. 87 tests pass; smoke test OK.
- 2026-10-12: Step 6 done. `VaultService` (injectable `Clock`) enforces ARCHITECTURE §8 throttling: after n consecutive failures the next attempt must wait min(1s·2^(n-1), 30s). Attempts inside the wait throw `UnlockThrottledException` before the KDF runs and are not counted (even with the right password); a wrong password is recorded (`VaultMetadataDao.recordFailedUnlock`, atomic increment + `last_failed_at`, in one transaction) *before* `InvalidPasswordException` is thrown; success resets via `resetFailedUnlocks`. Clock moving backwards still requires the full delay. Unlocks are serialized. `UnlockStatus` exposed via `ApplicationConnector.getUnlockStatus()`; Swing unlock screen dropped its own client-side lockout and now shows the attempt count + countdown from the service (also on startup mid-wait). `InputValidator.validateEmail` → `validateUsername` (non-empty, ≤256 chars, no control chars); UI labels say Username. `MutableClock` moved to `testutil`. 100 tests pass; real-time console smoke test confirmed refusal, persistence across restart, and unlock after the wait.
- 2026-10-12: Step 7 done. `VaultService.rotateVault(old, new)`: validates new password, verifies old via shared `verifyMasterPassword` (same throttling/failure recording as unlock), new salt + current default `KdfParams` (so rotation upgrades params), then new `VaultRotationDao.rotate` runs ONE transaction: takes the write lock, loads all entries, re-encrypts each (decrypt old key → encrypt new key, same id/AAD, plaintext wiped), checks the id set matches, updates every entry + metadata (failed attempts reset), commits; any failure rolls back everything. Session key swapped only after commit; old/new passwords and keys wiped (also on failure). `ApplicationConnector.changeMasterPassword` runs it via `EntryService.exclusively` so no entry op interleaves (`withPassword`/`revealNotes` now synchronized too). Swing: "Change password" button + dialog in Dashboard; console harness option 8. Constructors now take `VaultRotationDao`. 115 tests (incl. real mid-transaction failures on entry 2 and on metadata, failure during re-encryption, wrong old password throttled, params upgraded, session stays unlocked); jar smoke test: rotate → restart → new works, old rejected.
