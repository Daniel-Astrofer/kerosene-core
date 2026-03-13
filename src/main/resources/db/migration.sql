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

