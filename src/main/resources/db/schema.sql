-- DOOF Vault schema, version 1 (see docs/ARCHITECTURE.md §6).
-- Applied once by ConnectionManager when a new vault file is created.

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
