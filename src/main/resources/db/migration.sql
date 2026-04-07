-- Migrations incrementais aplicadas automaticamente na startup via spring.sql.init
-- Cada bloco é idempotente (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS)

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 001: Adicionar coluna totp_secret na tabela financial.wallets
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE financial.wallets
    ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(255) NOT NULL DEFAULT '';

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 002: Critical Performance Indexes (Issue 2.5)
-- All indexes use IF NOT EXISTS — safe to re-run on restart.
-- ─────────────────────────────────────────────────────────────────────────────

-- Ledger table
CREATE INDEX IF NOT EXISTS idx_ledger_wallet_id   ON financial.ledger(wallet_id);
CREATE INDEX IF NOT EXISTS idx_ledger_nonce        ON financial.ledger(nonce);
CREATE INDEX IF NOT EXISTS idx_ledger_last_hash    ON financial.ledger(last_hash);

-- Wallet table
CREATE INDEX IF NOT EXISTS idx_wallet_user_id      ON financial.wallets(user_id);

-- Ledger transaction history
CREATE INDEX IF NOT EXISTS idx_ledger_history_receiver ON financial.ledger_transaction_history(receiver_user_id);
CREATE INDEX IF NOT EXISTS idx_ledger_history_sender   ON financial.ledger_transaction_history(sender_identifier);
CREATE INDEX IF NOT EXISTS idx_ledger_history_created  ON financial.ledger_transaction_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_history_txid     ON financial.ledger_transaction_history(blockchain_txid)
    WHERE blockchain_txid IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ledger_history_status   ON financial.ledger_transaction_history(status);

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 003: Adicionar coluna deposit_address em financial.wallets
-- Armazena o endereço Bitcoin público (P2WPKH/Bech32 bc1q...) do usuário.
-- É usado para identificar a carteira de destino em depósitos on-chain.
-- DISTINTO do campo `address` que armazena o hash criptografado da passphrase.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE financial.wallets
    ADD COLUMN IF NOT EXISTS deposit_address VARCHAR(100) NULL;

-- Índice único para garantir que nenhum endereço Bitcoin seja compartilhado entre carteiras
CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_deposit_address
    ON financial.wallets(deposit_address)
    WHERE deposit_address IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 004: Passkey Schema Harmonization
-- Hibernate 'update' often creates a mess when renaming columns with constraints.
-- We drop the legacy 'public_key' to resolve NOT NULL violations.
-- ─────────────────────────────────────────────────────────────────────────────

-- Remove legacy column directly (PostgreSQL 9.0+ supports IF EXISTS)
ALTER TABLE auth.passkey_credentials DROP COLUMN IF EXISTS public_key;

-- Ensure device_name exists (idempotent)
ALTER TABLE auth.passkey_credentials ADD COLUMN IF NOT EXISTS device_name VARCHAR(255);

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 005: Dev balance claim guard
-- Prevents the development balance injector from crediting a test balance more
-- than once for the same user.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE auth.users_credentials
    ADD COLUMN IF NOT EXISTS test_balance_claimed BOOLEAN NOT NULL DEFAULT FALSE;

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 006: XPUB derivation state for deterministic wallet addresses
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE financial.wallets
    ADD COLUMN IF NOT EXISTS xpub TEXT;

ALTER TABLE financial.wallets
    ADD COLUMN IF NOT EXISTS last_derived_index INTEGER NOT NULL DEFAULT -1;

UPDATE financial.wallets
    SET last_derived_index = -1
    WHERE last_derived_index IS NULL;

ALTER TABLE financial.wallets
    ALTER COLUMN last_derived_index SET DEFAULT -1;

ALTER TABLE financial.wallets
    ALTER COLUMN last_derived_index SET NOT NULL;
