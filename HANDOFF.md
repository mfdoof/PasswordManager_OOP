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
- Unchanged original code in `src/com/doof/passwordmanager/` (no build tool, IntelliJ `.idea` files, MySQL connector).
- Existing layers: `model`, `dao` (JDBC), `service`, `util` (PasswordHasher = PBKDF2, PasswordEncryptor = AES-GCM, InputValidator), `db/ConnectionManager`, `app` (Swing `Main`, `Dashboard`; also `ConsolePasswordManager`).

## Known issues in the original
1. Hardcoded DB credentials `appuser/appuser` in `ConnectionManager` (also in git history).
2. Auth hash and encryption key derived from same PBKDF2 call/salt.
3. PBKDF2 at only 100k iterations.
4. Email and website stored in plaintext.
5. All passwords decrypted into the table model at load.
6. No unlock throttling.
7. `rotateVault` unimplemented.
8. No build tool, no tests, thin README, two entry points.

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
1. [ ] Maven setup: `pom.xml`, move to `src/main/java`, deps (`sqlite-jdbc`, `bcprov`, JUnit 5), shade plugin; remove `.idea`, `*.iml`, MySQL lib; update `.gitignore` (`target/`, `*.db`).
2. [ ] SQLite migration: new schema (ARCHITECTURE §6), `ConnectionManager` with path + perms checks, rewrite DAOs.
3. [ ] Crypto: Argon2id + HKDF, AAD, tests (round-trip, wrong password, tampered ciphertext).
4. [ ] Encrypt all entry fields; in-memory search.
5. [ ] Decrypt on demand + wipe.
6. [ ] Persistent unlock throttling.
7. [ ] Master password rotation (single transaction).
8. [ ] `SECURITY.md` + README rewrite.

Then Phase 2 (TUI) and Phase 3 (deployment) per ARCHITECTURE §11.

## Progress log
- 2026-10-11: Reviewed codebase, agreed on design, wrote ARCHITECTURE.md and this handoff. No code changes yet.
