-- ============================================================
-- Script SQL para Testar Transações Bitcoin (MODO MOCK)
-- Execute estes comandos no seu banco PostgreSQL
-- ============================================================

-- 1. Criar Payment Links de Teste
INSERT INTO payment_links (id, user_id, amount_btc, description, deposit_address, status, expires_at, created_at, paid_at, completed_at)
VALUES
  ('pay_test001', 1, 0.5, 'Curso Bitcoin 101', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 'pending', NOW() + INTERVAL '1 hour', NOW(), NULL, NULL),
  ('pay_test002', 1, 1.0, 'Pagamento de Serviço', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 'paid', NOW() + INTERVAL '2 hours', NOW(), NOW(), NULL),
  ('pay_test003', 1, 0.25, 'Anuidade Plataforma', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 'completed', NOW() + INTERVAL '3 hours', NOW(), NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '5 minutes'),
  ('pay_test004', 2, 2.0, 'Investimento', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 'pending', NOW() + INTERVAL '30 minutes', NOW(), NULL, NULL);

-- 2. Criar Depósitos de Teste
INSERT INTO deposits (user_id, txid, from_address, to_address, amount_btc, confirmations, status, created_at, confirmed_at)
VALUES
  (1, 'mock_deposit_001', '1XYZtest001', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 0.5, 5, 'confirmed', NOW() - INTERVAL '1 day', NOW() - INTERVAL '23 hours'),
  (1, 'mock_deposit_002', '1XYZtest002', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 1.0, 10, 'credited', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),
  (1, 'mock_deposit_003', '1XYZtest003', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 0.25, 2, 'confirmed', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '30 minutes'),
  (2, 'mock_deposit_004', '1XYZtest004', '1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP', 2.0, 3, 'confirmed', NOW() - INTERVAL '20 minutes', NOW() - INTERVAL '10 minutes');

-- 3. Consultar Payment Links
SELECT 'PAYMENT LINKS' as tipo, id, user_id, amount_btc, status, expires_at FROM payment_links;

-- 4. Consultar Depósitos
SELECT 'DEPOSITS' as tipo, txid, user_id, from_address, amount_btc, confirmations, status FROM deposits;

-- 5. Total de Depósitos Confirmados por Usuário
SELECT 
  user_id,
  COUNT(*) as total_deposits,
  SUM(amount_btc) as total_btc,
  SUM(CASE WHEN status = 'credited' THEN amount_btc ELSE 0 END) as credited_btc
FROM deposits
GROUP BY user_id;

-- 6. Status dos Payment Links
SELECT 
  status,
  COUNT(*) as quantidade,
  SUM(amount_btc) as total_btc
FROM payment_links
GROUP BY status;

-- 7. Limpar dados de teste (se necessário)
-- DELETE FROM payment_links WHERE id LIKE 'pay_test%';
-- DELETE FROM deposits WHERE txid LIKE 'mock_deposit_%';
