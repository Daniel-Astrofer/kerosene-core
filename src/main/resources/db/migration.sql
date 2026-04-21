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

ALTER TABLE auth.users_credentials
    ADD COLUMN IF NOT EXISTS shamir_total_shares INTEGER;

ALTER TABLE auth.users_credentials
    ADD COLUMN IF NOT EXISTS shamir_threshold INTEGER;

ALTER TABLE auth.users_credentials
    ADD COLUMN IF NOT EXISTS multisig_threshold INTEGER NOT NULL DEFAULT 2;

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

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 007: External network wallet metadata
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE financial.wallets
    ADD COLUMN IF NOT EXISTS lightning_address VARCHAR(255);

ALTER TABLE financial.wallets
    ADD COLUMN IF NOT EXISTS external_wallet_reference VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_lightning_address
    ON financial.wallets(lightning_address)
    WHERE lightning_address IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 008: External network transfers and custody events
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financial.network_transfers (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth.users_credentials(id) ON DELETE CASCADE,
    wallet_id BIGINT NOT NULL REFERENCES financial.wallets(id) ON DELETE CASCADE,
    wallet_name_snapshot VARCHAR(255) NOT NULL,
    network VARCHAR(32) NOT NULL,
    transfer_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    destination TEXT,
    external_reference VARCHAR(255),
    invoice_data TEXT,
    amount_btc NUMERIC(19, 8),
    network_fee_btc NUMERIC(19, 8),
    platform_fee_btc NUMERIC(19, 8),
    total_debited_btc NUMERIC(19, 8),
    context TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_network_transfers_user_created
    ON financial.network_transfers(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_network_transfers_wallet
    ON financial.network_transfers(wallet_id);

CREATE INDEX IF NOT EXISTS idx_network_transfers_status
    ON financial.network_transfers(status);

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 010: Inbound network transfer lifecycle metadata
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE financial.network_transfers
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_network_transfers_type_status
    ON financial.network_transfers(transfer_type, status);

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 009: Mining marketplace inspired by hashpower rentals
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financial.mining_rig_offers (
    id BIGSERIAL PRIMARY KEY,
    rig_code VARCHAR(64) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    hash_unit VARCHAR(16) NOT NULL,
    price_per_unit_day_btc NUMERIC(19, 8) NOT NULL,
    projected_btc_yield_per_unit_day NUMERIC(19, 8) NOT NULL,
    projected_yield_multiplier NUMERIC(10, 8) NOT NULL DEFAULT 1.00000000,
    available_hashrate NUMERIC(19, 8) NOT NULL,
    min_rental_hours INTEGER NOT NULL DEFAULT 1,
    max_rental_hours INTEGER NOT NULL DEFAULT 168,
    provider VARCHAR(64) NOT NULL DEFAULT 'KEROSENE_INTERNAL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mining_rig_offers_active
    ON financial.mining_rig_offers(active);

CREATE INDEX IF NOT EXISTS idx_mining_rig_offers_algorithm
    ON financial.mining_rig_offers(algorithm);

-- Default mining catalog seed. Keep this outside application services so runtime
-- reads do not mutate catalog state.
INSERT INTO financial.mining_rig_offers (
    rig_code,
    display_name,
    algorithm,
    hash_unit,
    price_per_unit_day_btc,
    projected_btc_yield_per_unit_day,
    projected_yield_multiplier,
    available_hashrate,
    min_rental_hours,
    max_rental_hours,
    provider,
    active
)
SELECT
    'sha256-hydro-240',
    'Hydro SHA256 240TH',
    'SHA256',
    'TH',
    0.00000850,
    0.00000720,
    0.98500000,
    1200.00000000,
    1,
    168,
    'KEROSENE_INTERNAL',
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM financial.mining_rig_offers WHERE rig_code = 'sha256-hydro-240'
);

INSERT INTO financial.mining_rig_offers (
    rig_code,
    display_name,
    algorithm,
    hash_unit,
    price_per_unit_day_btc,
    projected_btc_yield_per_unit_day,
    projected_yield_multiplier,
    available_hashrate,
    min_rental_hours,
    max_rental_hours,
    provider,
    active
)
SELECT
    'sha256-pro-150',
    'Pro SHA256 150TH',
    'SHA256',
    'TH',
    0.00000720,
    0.00000610,
    0.98250000,
    900.00000000,
    1,
    168,
    'KEROSENE_INTERNAL',
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM financial.mining_rig_offers WHERE rig_code = 'sha256-pro-150'
);

INSERT INTO financial.mining_rig_offers (
    rig_code,
    display_name,
    algorithm,
    hash_unit,
    price_per_unit_day_btc,
    projected_btc_yield_per_unit_day,
    projected_yield_multiplier,
    available_hashrate,
    min_rental_hours,
    max_rental_hours,
    provider,
    active
)
SELECT
    'scrypt-rack-18g',
    'Scrypt Rack 18GH',
    'SCRYPT',
    'GH',
    0.00012000,
    0.00010100,
    0.98000000,
    180.00000000,
    1,
    72,
    'KEROSENE_INTERNAL',
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM financial.mining_rig_offers WHERE rig_code = 'scrypt-rack-18g'
);

CREATE TABLE IF NOT EXISTS financial.mining_allocations (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth.users_credentials(id) ON DELETE CASCADE,
    wallet_id BIGINT NOT NULL REFERENCES financial.wallets(id) ON DELETE CASCADE,
    rig_id BIGINT NOT NULL REFERENCES financial.mining_rig_offers(id) ON DELETE RESTRICT,
    wallet_name_snapshot VARCHAR(255) NOT NULL,
    rig_name_snapshot VARCHAR(255) NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    hash_unit VARCHAR(16) NOT NULL,
    allocated_hashrate NUMERIC(19, 8) NOT NULL,
    duration_hours INTEGER NOT NULL,
    rental_cost_btc NUMERIC(19, 8) NOT NULL,
    projected_gross_yield_btc NUMERIC(19, 8) NOT NULL,
    projected_net_yield_btc NUMERIC(19, 8) NOT NULL,
    payout_address TEXT,
    pool_url TEXT,
    worker_name VARCHAR(255),
    provider_rental_reference VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    refunded_amount_btc NUMERIC(19, 8),
    settled_at TIMESTAMP,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mining_allocations_user_created
    ON financial.mining_allocations(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_mining_allocations_status
    ON financial.mining_allocations(status);

CREATE INDEX IF NOT EXISTS idx_mining_allocations_wallet
    ON financial.mining_allocations(wallet_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 010: Treasury configuration for real audit xpub scanning
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financial.treasury_config (
    id BIGSERIAL PRIMARY KEY,
    max_withdraw_limit NUMERIC(19, 8) NOT NULL DEFAULT 1.00000000,
    audit_xpub TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 011: Persistent idempotency for on-chain deposits
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financial.processed_transactions (
    id UUID PRIMARY KEY,
    txid VARCHAR(128) NOT NULL UNIQUE,
    source VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_transactions_txid
    ON financial.processed_transactions(txid);
