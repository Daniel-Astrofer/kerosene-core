# 🧪 Guia de Testes - Transações Bitcoin (MODO MOCK)

## 📋 Visão Geral

Este guia mostra como testar o sistema de transações Bitcoin **sem usar BTC real**.

---

## ⚙️ 1. Ativar MODO MOCK

Edite `application.properties`:

```properties
bitcoin.mock-mode=true
```

Agora o servidor:
- ✅ Aceita TXIDs fake
- ✅ Simula validações
- ✅ Não faz chamadas reais à Blockchain.info
- ✅ Retorna respostas realistas

---

## 🧪 2. Testes com cURL

### A. Criar Payment Link

```bash
curl -X POST http://localhost:8080/transactions/create-payment-link \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer seu_token_jwt" \
  -d '{
    "amount": 0.5,
    "description": "Curso Bitcoin 101"
  }'
```

**Resposta:**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "userId": 1,
  "amountBtc": 0.5,
  "description": "Curso Bitcoin 101",
  "depositAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "status": "pending",
  "expiresAt": "2026-02-11T12:30:00"
}
```

Salve o `id` para próximos testes.

---

### B. Consultar Payment Link

```bash
curl -X GET http://localhost:8080/transactions/payment-link/pay_a1b2c3d4e5f6 \
  -H "Authorization: Bearer seu_token_jwt"
```

---

### C. Confirmar Pagamento (MOCK - sem BTC real!)

```bash
curl -X POST http://localhost:8080/transactions/payment-link/pay_a1b2c3d4e5f6/confirm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer seu_token_jwt" \
  -d '{
    "txid": "mock_txid_abc123def456",
    "fromAddress": "1TestAddress123"
  }'
```

**Resposta:**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "status": "paid",
  "txid": "mock_txid_abc123def456",
  "paidAt": "2026-02-11T11:35:00"
}
```

---

### D. Liberar Valor

```bash
curl -X POST http://localhost:8080/transactions/payment-link/pay_a1b2c3d4e5f6/complete \
  -H "Authorization: Bearer seu_token_jwt"
```

**Resposta:**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "status": "completed",
  "completedAt": "2026-02-11T11:35:15"
}
```

---

## 📲 3. Testes com Postman

1. Importe `Postman_Bitcoin_Transactions.json`:
   - File → Import → Selecione o arquivo
   
2. Configure as variáveis:
   - `{{base_url}}` = `http://localhost:8080`
   - `{{jwt_token}}` = Seu token JWT real

3. Execute os testes na ordem:
   1. Criar Payment Link
   2. Consultar Payment Link
   3. Confirmar Pagamento
   4. Liberar Valor
   5. Listar Payment Links

---

## 🗄️ 4. Inserir Dados de Teste no Banco

Execute `test_data_mock.sql`:

```bash
psql -U api_system -d kerosene -f test_data_mock.sql
```

Isso criará:
- 4 Payment Links
- 4 Depósitos de teste

---

## 5. Scripts Bash

Execute `test_transactions_mock.sh`:

```bash
bash test_transactions_mock.sh
```

Testa todo o fluxo automaticamente.

---

## ✅ Validações no MODO MOCK

Quando você chama `/confirm`, o servidor valida:

| Validação | Resultado |
|-----------|-----------|
| TXID não-vazio | ✅ Requerido |
| Endereço não-vazio | ✅ Requerido |
| Valor positivo | ✅ Requerido |
| TXID único | ✅ Verifica banco |
| Link não-expirado | ✅ Verifica data |
| Link status "pending" | ✅ Verifica banco |

Se tudo OK:
```
✅ [MOCK] TX validada: mock_txid_abc123...
   Endereço: 1TestAddress...
   Valor recebido: 0.5 BTC
   Confirmações: 1 (simulado)
```

---

## 🔄 Fluxo Completo de Teste

```
1. POST /create-payment-link
   → Cria link com status "pending"

2. GET /payment-link/{id}
   → Mostra link (status: pending)

3. POST /payment-link/{id}/confirm
   → Valida TX (MOCK)
   → Muda status para "paid"
   → Salva TXID

4. POST /payment-link/{id}/complete
   → Muda status para "completed"
   → Valor é "liberado"

5. GET /payment-links
   → Lista todos os links do usuário
```

---

## 🧪 Casos de Teste

### Teste 1: Pagamento Completo
```
create-payment-link
  ↓ (status: pending)
confirm-payment
  ↓ (status: paid)
complete-payment
  ↓ (status: completed) ✅
```

### Teste 2: Link Expirado
```
create-payment-link (expires in 60 min)
  ↓ (espera mais de 60 min)
get-payment-link
  ↓ (status: expired) ✅
```

### Teste 3: Valor Incorreto
```
confirm-payment
  → Rejeita se valor < esperado ✅
```

### Teste 4: TXID Duplicado
```
confirm-payment (txid: abc123)
confirm-payment (txid: abc123) novamente
  → Rejeita TXID duplicado ✅
```

---

## 📊 Consultar Dados de Teste

```bash
# Payment Links
curl -X GET http://localhost:8080/transactions/payment-links \
  -H "Authorization: Bearer seu_token"

# Depósitos
curl -X GET http://localhost:8080/transactions/deposits \
  -H "Authorization: Bearer seu_token"

# Saldo de Depósitos
curl -X GET http://localhost:8080/transactions/deposit-balance \
  -H "Authorization: Bearer seu_token"
```

---

## 🔄 Alternância entre MODO MOCK e REAL

### MODO MOCK (Desenvolvimento)
```properties
bitcoin.mock-mode=true
```

### MODO REAL (Produção)
```properties
bitcoin.mock-mode=false
```

Sem necessidade de mudar código!

---

## 🛠️ Solução de Problemas

### Erro: "Token inválido"
- Obtenha um JWT real via `/auth/login`
- Copie o token para o header `Authorization`

### Erro: "Payment link não encontrado"
- Verifique se usou o ID correto
- Confirme que foi criado no passo anterior

### Erro: "Payment link já foi processado"
- Você já confirmou/completou este link
- Crie um novo com `/create-payment-link`

---

## 📝 Notas

- ⚠️ MODO MOCK é apenas para testes
- ⚠️ Não use em produção
- ✅ Banco de dados real é usado (registra tudo)
- ✅ Validações funcionam normalmente
- ✅ Apenas a API externa é mockada

---

**Pronto para testar!** 🚀
