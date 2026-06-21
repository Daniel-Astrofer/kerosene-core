-- Limita uma carteira ativa/criando por usuario e metodo de custodia.
CREATE UNIQUE INDEX IF NOT EXISTS ux_wallets_core_user_kind_active_custody
ON financial.wallets_core (user_id, kind)
WHERE status IN ('CREATING', 'ACTIVE', 'FROZEN', 'ROTATING_ADDRESS');
