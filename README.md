# DOOF Vault

A local, single-user password manager in Java. The whole vault is one encrypted SQLite file, protected by a master password with Argon2id, HKDF and AES-256-GCM.

It started as a university OOP project (Java + Swing + MySQL) and is being rebuilt with a security-first design. Phase 1 (hardening) is complete; a terminal UI is next.

## Features

- **Strong key derivation:** Argon2id (64 MiB, 3 iterations), with parameters stored per vault so they can be raised later
- **Separate keys:** HKDF splits the master key into an auth key (stored) and an encryption key (memory only)
- **Everything encrypted:** site, username, password and notes are one AES-256-GCM blob per entry, with the entry id bound as AAD to stop row swapping
- **Decrypt on demand:** only site + username are in memory after unlock; passwords are decrypted when revealed or copied, then wiped
- **Unlock throttling:** exponential delay (1s → 30s cap) that survives restarts
- **Master password rotation:** re-encrypts everything in a single transaction; any failure rolls back
- **File hardening:** vault created as `600` in a `700` directory, warning if permissions are looser; no credentials anywhere
- **Clipboard auto-clear** after 20 seconds; reveal re-masks after 10 seconds

## Why it's built this way

Each choice answers a specific threat: a stolen vault file, guessing, tampering, a password lingering in memory. [SECURITY.md](SECURITY.md) covers the threat model, crypto and known limits. It also explains why throttling does not stop offline attacks on a copied file. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) has the full design: layers, key hierarchy, schema and the planned TUI.

## Requirements

- Java 17+
- Maven 3.9+

## Build and run

```bash
mvn package                                   # builds and runs the tests
java -jar target/doof-vault-0.1.0-SNAPSHOT.jar
```

The first run creates a new vault. Choose where it lives with:

```bash
java -jar target/doof-vault-0.1.0-SNAPSHOT.jar --vault /path/to/vault.db
DOOF_VAULT_PATH=/path/to/vault.db java -jar target/doof-vault-0.1.0-SNAPSHOT.jar
```

The order of precedence is `--vault`, then `DOOF_VAULT_PATH`, then `~/.doof/vault.db`.

The JAR now starts the terminal UI (Phase 2, in progress; currently a skeleton). Flags: `--ascii` for plain `+ - |` borders, `--no-color` (or `NO_COLOR`) for monochrome. Until the TUI is complete, the original Swing app is still available with `--swing`. A minimal console harness, used for testing, is also included:

```bash
java -cp target/doof-vault-0.1.0-SNAPSHOT.jar com.doof.passwordmanager.service.ConsolePasswordManager --vault /tmp/test.db
```

## Tests

```bash
mvn test
```

The suite covers the crypto (including an RFC 5869 HKDF vector), tampering and row-swap detection, the DAOs against a temporary vault file, throttling with an injectable clock, decrypt-on-demand and wiping, and rotation. The rotation tests include real mid-transaction failures.

## Project structure

```
src/main/java/com/doof/passwordmanager/
├── app/       Swing UI (Main, Dashboard) and wiring (AppBootstrap); replaced by the TUI in Phase 2
├── service/   VaultService (create/unlock/lock/rotate, throttling, auto-lock), EntryService (CRUD, search, reveal)
├── crypto/    Kdf + KdfParams (Argon2id), KeyHierarchy (HKDF), AesGcm, SecureWipe
├── dao/       JDBC DAOs for metadata, entries and rotation; they only ever see encrypted bytes
├── db/        ConnectionManager: vault path, file permissions, schema setup
├── model/     Entry, VaultMetadata
└── util/      FlatJson, InputValidator
src/main/resources/db/schema.sql
src/test/java/...  JUnit 5 tests
```

## Roadmap

- [x] **Phase 1: Hardening.** Maven, SQLite, Argon2id + HKDF + AES-GCM, encrypted fields, decrypt on demand, throttling, rotation, docs
- [ ] **Phase 2: TUI.** Lanterna terminal UI (works locally, over SSH and headless), idle auto-lock, password generator; remove Swing
- [ ] **Phase 3: Deployment.** Homelab deployment, encrypted backups, file-ownership hardening

## Author

Mave ([@mfdoof](https://github.com/mfdoof))

## License

[MIT](LICENSE)
