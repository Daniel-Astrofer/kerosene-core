CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS financial;

-- =============================================
-- AUTH SCHEMA
-- =============================================

-- Vouchers table for onboarding
CREATE TABLE IF NOT EXISTS auth.vouchers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(255) UNIQUE,
    txid VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    value_sats INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP
);

-- Main users table
CREATE TABLE IF NOT EXISTS auth.users_credentials (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    passphrase VARCHAR(255),
    totp_secret VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    is_active BOOLEAN DEFAULT FALSE,
    failed_login_attempts INTEGER DEFAULT 0,
    account_security VARCHAR(20) DEFAULT 'STANDARD',
    passkey_transaction_auth BOOLEAN DEFAULT FALSE,
    platform_cosigner_secret TEXT,
    voucher_id UUID UNIQUE REFERENCES auth.vouchers(id)
);

-- Passkey credentials table (FIDO2 / WebAuthn)
CREATE TABLE IF NOT EXISTS auth.passkey_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id BYTEA UNIQUE NOT NULL,
    user_handle BYTEA NOT NULL,
    public_key_cose BYTEA NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES auth.users_credentials(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_passkey_user_id ON auth.passkey_credentials(user_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON auth.users_credentials(username);

-- =============================================
-- FINANCIAL SCHEMA
-- =============================================

-- Wallets table
CREATE TABLE IF NOT EXISTS financial.wallets (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES auth.users_credentials(id) ON DELETE CASCADE,
    address VARCHAR(255) NOT NULL, -- Actually used as passphraseHash
    name VARCHAR(255) NOT NULL,
    totp_secret VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    UNIQUE(user_id, name)
);

-- Ledger table
CREATE TABLE IF NOT EXISTS financial.ledger (
    id SERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES financial.wallets(id) ON DELETE CASCADE,
    balance TEXT NOT NULL, -- ALE Encrypted
    balance_signature VARCHAR(256),
    nonce INTEGER NOT NULL DEFAULT 0,
    last_hash VARCHAR(256) NOT NULL,
    context VARCHAR(256) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ledger Transaction History table
CREATE TABLE IF NOT EXISTS financial.ledger_transaction_history (
    id UUID PRIMARY KEY,
    sender_identifier VARCHAR(255) NOT NULL,
    sender_user_id BIGINT,
    receiver_identifier VARCHAR(255) NOT NULL,
    receiver_user_id BIGINT,
    transaction_type VARCHAR(50) NOT NULL,
    amount NUMERIC(18, 8) NOT NULL,
    status VARCHAR(50) NOT NULL,
    network_fee NUMERIC(18, 8),
    blockchain_txid VARCHAR(255),
    context TEXT,
    created_at TIMESTAMP NOT NULL,
    confirmations INTEGER
);

-- Ledger Entries (Audit)
CREATE TABLE IF NOT EXISTS financial.ledger_entries (
    id UUID PRIMARY KEY,
    tx_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount_net NUMERIC(18, 8) NOT NULL,
    fee_amount NUMERIC(18, 8) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Ledger Transactions (Metadata)
CREATE TABLE IF NOT EXISTS financial.ledger_transactions (
    id BIGSERIAL PRIMARY KEY,
    txid VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    to_address TEXT, -- ALE Encrypted
    amount NUMERIC(19, 8),
    message TEXT, -- ALE Encrypted
    created_at TIMESTAMP NOT NULL
);
