# DOOF Vault

[![CI](https://github.com/mfdoof/PasswordManager_OOP/actions/workflows/ci.yml/badge.svg)](https://github.com/mfdoof/PasswordManager_OOP/actions/workflows/ci.yml)

A local, single-user password manager for the terminal. The whole vault is one encrypted SQLite file, protected by a master password with Argon2id, HKDF and AES-256-GCM. It is keyboard-only and works locally, over SSH and on a headless console.

It started as a university OOP project (Java + Swing + MySQL) and was rebuilt with a security-first design and a terminal UI.

<!-- Recording: replace with an asciinema embed or GIF -->
> Demo recording coming soon.

## Features

- **Strong key derivation:** Argon2id (64 MiB, 3 iterations), with parameters stored per vault so they can be raised later
- **Separate keys:** HKDF splits the master key into an auth key (stored) and an encryption key (memory only)
- **Everything encrypted:** site, username, password and notes are one AES-256-GCM blob per entry, with the entry id bound as AAD to stop row swapping
- **Decrypt on demand:** only site + username are in memory after unlock; a password or notes are decrypted when revealed or copied, then wiped
- **Unlock throttling:** exponential delay (1s to 30s) that survives restarts
- **Idle auto-lock:** 5 minutes without a keypress locks the vault, wipes keys and clears the screen
- **Clipboard with auto-clear:** a copied password is cleared after 20 seconds, on lock and on quit (only if it is still ours)
- **Password generator:** SecureRandom, length 8-128, four character sets
- **Master password rotation:** re-encrypts everything in a single transaction; any failure rolls back
- **File hardening:** vault created as `600` in a `700` directory, warning if permissions are looser; no credentials anywhere

## Why it's built this way

Each choice answers a specific threat: a stolen vault file, guessing, tampering, a password lingering in memory or on screen. [SECURITY.md](SECURITY.md) covers the threat model, crypto and known limits, including why throttling does not stop offline attacks on a copied file. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) has the design (layers, key hierarchy, schema) and [docs/TUI_DESIGN.md](docs/TUI_DESIGN.md) the terminal UI.

## Requirements

- Java 17+
- Maven 3.9+ (to build)
- A terminal of at least 80x24

## Install (macOS and Linux)

Needs Java 17 or newer (for example [Temurin](https://adoptium.net); macOS: `brew install --cask temurin`).

Quick install of the latest release:

```bash
curl -fsSL https://raw.githubusercontent.com/mfdoof/PasswordManager_OOP/main/install-doof-vault.sh | bash
```

Safer: download the script, read it, then run it:

```bash
curl -fsSLO https://raw.githubusercontent.com/mfdoof/PasswordManager_OOP/main/install-doof-vault.sh
less install-doof-vault.sh
bash install-doof-vault.sh                    # latest release
bash install-doof-vault.sh --version 2.0.1    # or a specific one
```

The script downloads `doof-vault-<version>.jar` and `SHA256SUMS` from this repository's GitHub Releases over HTTPS only, verifies the checksum (and installs nothing if it does not match), checks for Java 17+, then installs:

- `~/.local/share/doof/doof-vault.jar` (and a `VERSION` file)
- `~/.local/bin/doofv`, a small launcher that finds Java 17+ (`$JAVA_HOME`, then `PATH`) and runs the jar

If `~/.local/bin` is not on your `PATH`, it tells you the line to add. Then run `doofv` (or `doofv backup`).

- **Upgrade:** run the script again; it installs the latest release, or does nothing if that version is already installed and intact.
- **Uninstall:** `bash install-doof-vault.sh --uninstall` (or `curl -fsSL .../install-doof-vault.sh | bash -s -- --uninstall`). It removes only the launcher and the jar; your vault and backups in `~/.doof` are kept.

Installer exit codes: 0 ok, 1 failed (nothing installed or changed), 2 usage error.

## Releases

Each release on the [Releases page](https://github.com/mfdoof/PasswordManager_OOP/releases) has the runnable jar `doof-vault-<version>.jar` and a `SHA256SUMS` file. They are built and tested by GitHub Actions from the tagged commit on `main`.

Download and verify by hand:

```bash
VERSION=2.0.1
BASE=https://github.com/mfdoof/PasswordManager_OOP/releases/download/v$VERSION
curl -fLO "$BASE/doof-vault-$VERSION.jar"
curl -fLO "$BASE/SHA256SUMS"

sha256sum -c SHA256SUMS          # Linux
shasum -a 256 -c SHA256SUMS      # macOS

java -jar doof-vault-$VERSION.jar
```

The check must print `doof-vault-<version>.jar: OK`; if it says `FAILED`, delete the jar and do not run it. Checksums catch corrupted or swapped downloads, but they come from the same release page, so they do not prove who published it; signed releases (cosign) are planned.

## Build from source

```bash
mvn package                     # runs the tests, builds target/doof-vault.jar
java -jar target/doof-vault.jar
```

The first run creates a new vault (master password: 12+ characters with upper case, lower case and a digit). Choose where it lives with:

```bash
java -jar target/doof-vault.jar --vault /path/to/vault.db
DOOF_VAULT_PATH=/path/to/vault.db java -jar target/doof-vault.jar
```

The order of precedence is `--vault`, then `DOOF_VAULT_PATH`, then `~/.doof/vault.db`.

| Flag | Effect |
|---|---|
| `--vault <path>` | vault file to open or create |
| `--ascii` | plain `+ - \|` borders (also used automatically without a UTF-8 locale) |
| `--no-color` | monochrome; same as setting `NO_COLOR` |
| `-h`, `--help` | usage (also `doofv backup --help`) |

## Backup

```bash
doofv backup [--vault <path>] [--out <dir>] [--keep N]
# without the installer: java -jar doof-vault-<version>.jar backup ...
```

Copies the encrypted vault file with SQLite's online backup API (safe while the app is open), verifies the copy (integrity check, schema version, vault set up), saves it as `vault-YYYYMMDD-HHMMSS.db` with `600` permissions in `~/.doof/backups` (`700`), then keeps the newest 14 (`--keep`). It never asks for the master password; a backup is exactly as encrypted as the vault. Output is plain text for cron/launchd/systemd.

| Exit code | Meaning |
|---|---|
| 0 | backup written and verified (old copies pruned) |
| 1 | backup failed; no new file kept, nothing pruned |
| 2 | usage error |

### Scheduled backups

Ready-made jobs in [`contrib/`](contrib) run `doofv backup` every day at 03:00 (Java must be findable: a JDK installed system-wide, on `PATH`, or set `JAVA_HOME` in the job).

**macOS (launchd).** Logs go to `~/Library/Logs/doof-backup.log`; if the Mac is asleep at 03:00, the job runs when it wakes.

```bash
curl -fsSLo ~/Library/LaunchAgents/quest.doof.vault-backup.plist \
  https://raw.githubusercontent.com/mfdoof/PasswordManager_OOP/main/contrib/launchd/quest.doof.vault-backup.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/quest.doof.vault-backup.plist   # enable
launchctl kickstart -p gui/$(id -u)/quest.doof.vault-backup                            # run once now
tail ~/Library/Logs/doof-backup.log                                                     # check
launchctl print gui/$(id -u)/quest.doof.vault-backup | grep -E 'state|last exit code'

launchctl bootout gui/$(id -u)/quest.doof.vault-backup                                  # disable
rm ~/Library/LaunchAgents/quest.doof.vault-backup.plist
```

**Linux (systemd user timer).** Output goes to the journal; `Persistent=true` catches up on a missed run after the machine was off.

```bash
mkdir -p ~/.config/systemd/user
curl -fsSLo ~/.config/systemd/user/doof-backup.service https://raw.githubusercontent.com/mfdoof/PasswordManager_OOP/main/contrib/systemd/doof-backup.service
curl -fsSLo ~/.config/systemd/user/doof-backup.timer   https://raw.githubusercontent.com/mfdoof/PasswordManager_OOP/main/contrib/systemd/doof-backup.timer
systemctl --user daemon-reload
systemctl --user enable --now doof-backup.timer      # enable
systemctl --user start doof-backup.service           # run once now
systemctl --user list-timers doof-backup.timer       # next run
journalctl --user -u doof-backup.service -n 20       # check

systemctl --user disable --now doof-backup.timer     # disable
rm ~/.config/systemd/user/doof-backup.service ~/.config/systemd/user/doof-backup.timer
systemctl --user daemon-reload
```

User timers only run while you are logged in; to run them regardless, enable lingering once: `sudo loginctl enable-linger "$USER"`.

### Restore

1. Quit DOOF Vault (`q`) and make sure it is not running (`pgrep -f doof-vault.jar` prints nothing).
2. Pick a backup: `ls ~/.doof/backups`.
3. Keep the current vault aside, and move away a leftover journal file if there is one (SQLite would otherwise try to apply it to the restored file):
   ```bash
   cd ~/.doof
   cp -p vault.db vault.db.before-restore
   [ -e vault.db-journal ] && mv vault.db-journal vault.db-journal.before-restore
   ```
4. Copy the backup over the vault and make it owner-only:
   ```bash
   cp backups/vault-YYYYMMDD-HHMMSS.db vault.db
   chmod 600 vault.db
   ```
5. Run `doofv` and unlock with the master password that was current when that backup was taken. When everything looks right, delete `vault.db.before-restore`.

### Off-machine copies

A backup on the same disk does not survive losing the machine. No extra tooling is built in; either point `--out` at a folder that is synced or on a NAS (for example `doofv backup --out /Volumes/nas/doof-backups`, or a synced folder; `--keep` prunes per folder), or include `~/.doof/backups` in an existing backup tool such as [restic](https://restic.net). Backups are encrypted, but anyone who gets one can attempt offline guessing, so keep them somewhere private.

## Keys

| Key | Action |
|---|---|
| `j` / `k`, arrows, `gg` / `G` | move through the list |
| `/` | search site and username (`ESC` clears) |
| `s` / `n` | reveal password / notes for 10s |
| `c` / `u` | copy password (cleared after 20s) / username |
| `a` / `e` / `d` | add / edit / delete (asks `[y/N]`) |
| `p` | change master password |
| `l` / `q` | lock / quit (both wipe keys and clear the screen) |
| `?` | all keys |

In the entry form: `TAB` moves between fields, `ENTER` saves, `ESC` cancels. On the generator row `g` generates and `v` shows the password (`ctrl+g` / `ctrl+v` work from any field). Over SSH the clipboard is reported as unavailable; use `s` to reveal instead.

## Tests

```bash
mvn test
```

The suite covers the crypto (including an RFC 5869 HKDF vector), tampering and row-swap detection, the DAOs against a temporary vault file, throttling and idle lock with an injectable clock, decrypt-on-demand and wiping, rotation with real mid-transaction failures, and every screen of the TUI on Lanterna's in-memory terminal. Tests run headless and never touch the real clipboard.

## Project structure

```
src/main/java/com/doof/passwordmanager/
├── ui/tui/    TuiApp (entry point, main loop), ScreenManager, Theme, TuiContext
│   ├── screen/   Unlock, Create, Vault, entry form, change password, help
│   ├── widget/   SecretField, TextField, StrengthMeter, PolicyView, PasswordGenerator, StatusBar
│   └── clip/     SecureClipboard (auto-clear) over the AWT clipboard
├── app/       AppBootstrap: wires DAOs and services for an opened vault
├── service/   VaultService (create/unlock/lock/rotate, throttling, idle timer), EntryService (CRUD, reveal)
├── crypto/    Kdf + KdfParams (Argon2id), KeyHierarchy (HKDF), AesGcm, SecureWipe
├── dao/       JDBC DAOs for metadata, entries and rotation; they only ever see encrypted bytes
├── db/        ConnectionManager: vault path, file permissions, schema setup
├── model/     Entry, VaultMetadata
└── util/      FlatJson, InputValidator, PasswordPolicy
src/main/resources/db/schema.sql
src/test/java/...  JUnit 5 tests
```

## Roadmap

- [x] **Phase 1: Hardening.** Maven, SQLite, Argon2id + HKDF + AES-GCM, encrypted fields, decrypt on demand, throttling, rotation, docs
- [x] **Phase 2: TUI.** Lanterna terminal UI (local, SSH, headless), idle auto-lock, clipboard auto-clear, generator, change master password; Swing removed
- [ ] **Phase 3: Deployment.** Homelab deployment, encrypted backups, file-ownership hardening

## Author

Mave ([@mfdoof](https://github.com/mfdoof))

## License

[MIT](LICENSE)
