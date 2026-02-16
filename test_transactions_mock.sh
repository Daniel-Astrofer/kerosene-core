#!/bin/bash
# Script para testar transações Bitcoin em MODO MOCK
# Não requer BTC real - apenas simula as validações

echo "🚀 Teste de Transações Bitcoin (MODO MOCK)"
echo "=========================================="

BASE_URL="http://localhost:8080"
USER_ID="1"
JWT_TOKEN="seu_token_jwt_aqui"  # Obtenha um token real via /auth/login

# Headers
HEADERS="-H 'Content-Type: application/json' -H 'Authorization: Bearer $JWT_TOKEN'"

echo ""
echo "1️⃣  Criar Payment Link"
echo "====================="
curl -X POST "$BASE_URL/transactions/create-payment-link" \
  $HEADERS \
  -d '{
    "amount": 0.5,
    "description": "Curso Bitcoin 101"
  }' | jq .

# Salve o linkId da resposta
LINK_ID="pay_teste123"

echo ""
echo "2️⃣  Consultar Payment Link (antes do pagamento)"
echo "==============================================="
curl -X GET "$BASE_URL/transactions/payment-link/$LINK_ID" \
  $HEADERS | jq .

echo ""
echo "3️⃣  Confirmar Pagamento (MODO MOCK - sem BTC real)"
echo "=================================================="
curl -X POST "$BASE_URL/transactions/payment-link/$LINK_ID/confirm" \
  $HEADERS \
  -d '{
    "txid": "mock_txid_abc123def456",
    "fromAddress": "1XYZtest123"
  }' | jq .

echo ""
echo "4️⃣  Liberar Valor"
echo "================="
curl -X POST "$BASE_URL/transactions/payment-link/$LINK_ID/complete" \
  $HEADERS | jq .

echo ""
echo "5️⃣  Listar Todos os Payment Links do Usuário"
echo "============================================"
curl -X GET "$BASE_URL/transactions/payment-links" \
  $HEADERS | jq .

echo ""
echo "✅ Teste concluído!"
