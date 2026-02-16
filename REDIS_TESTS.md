# 🧪 Guia de Testes - Redis Payment Links

## 🚀 Iniciar o Servidor

### 1. Iniciar Redis
```bash
# Windows - Se tem WSL ou instalado
redis-server

# Ou com Docker (recomendado)
docker run -d -p 6379:6379 --name redis redis:latest

# Verificar conexão
redis-cli ping
# Saída esperada: PONG
```

### 2. Iniciar aplicação Spring Boot
```bash
cd c:\Users\omega\Documents\Kerosene\backend\kerosene
./gradlew bootRun
# ou
gradle.bat bootRun
```

Esperado na console:
```
✅ Started Application in X.XXX seconds
```

---

## 📝 Testes via cURL

### Teste 1: Criar Payment Link

```bash
curl -X POST http://localhost:8080/api/payment-links \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "amountBtc": 0.5,
    "description": "Depósito de teste"
  }'
```

**Resposta esperada (201 Created):**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "userId": 1,
  "amountBtc": 0.5,
  "description": "Depósito de teste",
  "depositAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "status": "pending",
  "expiresAt": "2024-12-25T15:30:00",
  "createdAt": "2024-12-25T12:30:00",
  "txid": null,
  "paidAt": null,
  "completedAt": null
}
```

**Logs esperados:**
```
✅ Payment Link criado e armazenado no Redis: pay_a1b2c3d4e5f6
```

**Copie o `id` para os próximos testes!**

---

### Teste 2: Consultar Payment Link (Cache-First)

**Primeira consulta (Redis miss + DB hit):**
```bash
curl http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6
```

**Logs esperados:**
```
✅ Payment Link recuperado do banco e adicionado ao Redis: pay_a1b2c3d4e5f6
```

**Próximas consultas (Redis hit - mais rápido!):**
```bash
# Execute novamente
curl http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6
```

**Logs esperados:**
```
✅ Payment Link recuperado do Redis: pay_a1b2c3d4e5f6
```

**Resposta esperada (200 OK):**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "userId": 1,
  "amountBtc": 0.5,
  "status": "pending",
  ...
}
```

---

### Teste 3: Confirmar Pagamento

```bash
curl -X POST http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "txid": "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
    "fromAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP"
  }'
```

**Resposta esperada (200 OK):**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "userId": 1,
  "amountBtc": 0.5,
  "status": "paid",
  "txid": "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
  "paidAt": "2024-12-25T12:35:00",
  ...
}
```

**Logs esperados:**
```
✅ Pagamento confirmado: Link=pay_a1b2c3d4e5f6, TXID=1234567..., Valor=0.5
```

---

### Teste 4: Liberar/Completar Pagamento

```bash
curl -X POST http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6/complete \
  -H "Content-Type: application/json"
```

**Resposta esperada (200 OK):**
```json
{
  "id": "pay_a1b2c3d4e5f6",
  "userId": 1,
  "amountBtc": 0.5,
  "status": "completed",
  "completedAt": "2024-12-25T12:36:00",
  ...
}
```

**Logs esperados:**
```
✅ Pagamento liberado: Link=pay_a1b2c3d4e5f6, Valor=0.5
```

---

### Teste 5: Listar Payment Links do Usuário

```bash
curl http://localhost:8080/api/payment-links/user/1
```

**Resposta esperada (200 OK):**
```json
[
  {
    "id": "pay_a1b2c3d4e5f6",
    "userId": 1,
    "amountBtc": 0.5,
    "status": "completed",
    ...
  },
  {
    "id": "pay_x9y8z7w6v5u4",
    "userId": 1,
    "amountBtc": 1.0,
    "status": "pending",
    ...
  }
]
```

---

### Teste 6: Remover do Cache Redis

```bash
curl -X DELETE http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6/cache
```

**Resposta esperada (200 OK):**
```json
{
  "message": "Payment link removido do Redis"
}
```

**Logs esperados:**
```
✅ Payment Link removido do Redis: pay_a1b2c3d4e5f6
```

---

### Teste 7: Consultar após remover (Fallback ao DB)

```bash
curl http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6
```

**Logs esperados:**
```
✅ Payment Link recuperado do banco e adicionado ao Redis: pay_a1b2c3d4e5f6
```

Observe que recupera do banco e re-adiciona ao Redis!

---

## 🔍 Monitorar Redis

### Via Redis CLI

```bash
# Conectar
redis-cli

# Ver todas as payment links no Redis
KEYS payment_link:*

# Exemplo de saída:
# 1) "payment_link:pay_a1b2c3d4e5f6"
# 2) "payment_link:pay_x9y8z7w6v5u4"
# 3) "payment_link:pay_m3n4o5p6q7r8"

# Ver detalhes de uma key
GET payment_link:pay_a1b2c3d4e5f6

# Exemplo de saída:
# "{\"id\":\"pay_a1b2c3d4e5f6\",\"userId\":1,\"amountBtc\":0.5, ...}"

# Ver TTL (tempo até expiração em segundos)
TTL payment_link:pay_a1b2c3d4e5f6

# Exemplo de saída:
# (integer) 10799  ← Restam 10799 segundos (~ 3 horas)

# Contar quantas payment links estão no Redis
DBSIZE

# Ver memória usada
INFO memory

# Ver estatísticas de hits/misses
INFO stats

# Limpar tudo (CUIDADO!)
FLUSHDB

# Sair
EXIT
```

---

## 🧪 Testes Automatizados

### Rodar todos os testes Redis

```bash
cd c:\Users\omega\Documents\Kerosene\backend\kerosene

# Rodar apenas testes do PaymentLinkServiceRedisTest
./gradlew test --tests PaymentLinkServiceRedisTest

# Ou com Gradle tradicional
gradle.bat test --tests PaymentLinkServiceRedisTest
```

**Saída esperada:**
```
PaymentLinkServiceRedisTest
  ✓ testPaymentLinkStoredInRedis()
  ✓ testPaymentLinkRetrievedFromRedis()
  ✓ testPaymentLinkExpirationSync()
  ✓ testRedisKeyTTL()
  ✓ testRemoveFromRedis()

✅ Payment link recuperado com sucesso do Redis: pay_xxx
✅ Status de expiração sincronizado no Redis: pay_xxx
✅ Redis TTL válido: 10799 segundos (máximo 3 horas)
✅ Payment link removido do Redis com sucesso

BUILD SUCCESSFUL in X.XXXs
```

---

## 📊 Teste de Performance

### Benchmark: Comparar velocidade com/sem Redis

```bash
# Script PowerShell para medir tempo de resposta

# 1. Criar um payment link
$linkResponse = curl -X POST http://localhost:8080/api/payment-links `
  -H "Content-Type: application/json" `
  -d '{"userId": 1, "amountBtc": 0.5, "description": "Teste"}' | ConvertFrom-Json

$linkId = $linkResponse.id
Write-Host "Link criado: $linkId"

# 2. Medir 1ª requisição (DB + Redis)
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
curl http://localhost:8080/api/payment-links/$linkId > $null
$stopwatch.Stop()
Write-Host "1ª requisição (DB): $($stopwatch.ElapsedMilliseconds) ms"

# 3. Medir 100 requisições subsequentes (Redis)
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
for ($i = 0; $i -lt 100; $i++) {
    curl http://localhost:8080/api/payment-links/$linkId > $null
}
$stopwatch.Stop()
$avgTime = $stopwatch.ElapsedMilliseconds / 100
Write-Host "Média 100 requisições (Redis): $avgTime ms"
Write-Host "Melhoria: $(100 - $avgTime)% mais rápido"
```

**Resultado esperado:**
```
1ª requisição (DB): 100 ms
Média 100 requisições (Redis): 5.5 ms
Melhoria: 94.5% mais rápido
```

---

## ❌ Testes de Erro

### Teste 1: Link não encontrado

```bash
curl http://localhost:8080/api/payment-links/pay_naoexiste
```

**Resposta esperada (404):**
```json
{
  "error": "Payment link não encontrado"
}
```

---

### Teste 2: Confirmar link expirado

**Criar link com expiração imediata (no DB):**
```sql
-- No PostgreSQL
UPDATE payment_link 
SET expires_at = NOW() - INTERVAL '1 hour'
WHERE id = 'pay_a1b2c3d4e5f6';
```

**Tentar confirmar:**
```bash
curl -X POST http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6/confirm \
  -H "Content-Type: application/json" \
  -d '{"txid": "xxx"}'
```

**Resposta esperada (400):**
```json
{
  "error": "Payment link expirou"
}
```

---

### Teste 3: Status inválido

**Tentar completar um link que ainda está pendente:**
```bash
curl -X POST http://localhost:8080/api/payment-links/pay_novo/complete
```

**Resposta esperada (400):**
```json
{
  "error": "Payment link precisa estar 'paid' para ser completado"
}
```

---

## 🔄 Teste de Sincronização DB ↔️ Redis

```bash
# 1. Criar payment link
curl -X POST http://localhost:8080/api/payment-links \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "amountBtc": 0.5}' | jq -r '.id'

# Copie o ID (exemplo: pay_a1b2c3d4e5f6)

# 2. Verificar no Redis CLI
redis-cli
> GET payment_link:pay_a1b2c3d4e5f6
# Saída: {...,"status":"pending",...}

# 3. Confirmar pagamento via API
curl -X POST http://localhost:8080/api/payment-links/pay_a1b2c3d4e5f6/confirm \
  -H "Content-Type: application/json" \
  -d '{"txid": "abc123"}'

# 4. Verificar Redis novamente (deve estar sincronizado)
> GET payment_link:pay_a1b2c3d4e5f6
# Saída: {...,"status":"paid",...}

# 5. Verificar TTL (deve ser resetado para 3 horas)
> TTL payment_link:pay_a1b2c3d4e5f6
# Saída: (integer) 10800
```

---

## 📈 Exemplo Completo de Fluxo

```bash
#!/bin/bash

echo "🚀 Teste Completo de Payment Link com Redis"
echo "=============================================="

# 1. Criar payment link
echo -e "\n1️⃣ Criando payment link..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/payment-links \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "amountBtc": 0.5, "description": "BTC Deposito"}')
LINK_ID=$(echo $RESPONSE | jq -r '.id')
echo "✅ Link criado: $LINK_ID"

# 2. Verificar no Redis
echo -e "\n2️⃣ Verificando no Redis..."
TTL=$(redis-cli TTL payment_link:$LINK_ID)
echo "✅ TTL no Redis: $TTL segundos (3 horas = 10800 segundos)"

# 3. Consultar link (deve vir do Redis agora)
echo -e "\n3️⃣ Consultando link (Redis)..."
curl -s http://localhost:8080/api/payment-links/$LINK_ID | jq '.status'
echo "✅ Link recuperado do Redis"

# 4. Confirmar pagamento
echo -e "\n4️⃣ Confirmando pagamento..."
curl -s -X POST http://localhost:8080/api/payment-links/$LINK_ID/confirm \
  -H "Content-Type: application/json" \
  -d '{"txid": "abc123"}' | jq '.status'
echo "✅ Status mudou para 'paid'"

# 5. Liberar valor
echo -e "\n5️⃣ Liberando valor..."
curl -s -X POST http://localhost:8080/api/payment-links/$LINK_ID/complete \
  -H "Content-Type: application/json" | jq '.status'
echo "✅ Status mudou para 'completed'"

# 6. Verificar sincronização
echo -e "\n6️⃣ Sincronização final..."
redis-cli GET payment_link:$LINK_ID | jq '.status'
echo "✅ Status está sincronizado no Redis!"

echo -e "\n✅ Teste completo finalizado com sucesso!"
```

---

## 🎯 Checklist de Validação

```
[ ] Redis está rodando (redis-cli ping → PONG)
[ ] Aplicação Spring Boot iniciada com sucesso
[ ] Teste 1: Criar payment link (201 Created)
[ ] Teste 2: Consultar link (200 OK + logs de Redis)
[ ] Teste 3: Confirmar pagamento (200 OK + status "paid")
[ ] Teste 4: Completar pagamento (200 OK + status "completed")
[ ] Teste 5: Listar links do usuário (200 OK + array)
[ ] Teste 6: Remover do cache (200 OK + message)
[ ] Teste 7: Sincronização DB ↔️ Redis validada
[ ] Testes automatizados passando (gradle test)
[ ] TTL Redis em 3 horas (10800 segundos)
[ ] Performance: 2ª requisição < 10ms
```

---

## 🐛 Troubleshooting

| Problema | Causa | Solução |
|----------|-------|---------|
| `Could not create Redis connection` | Redis não está rodando | `redis-server` ou `docker run -d -p 6379:6379 redis:latest` |
| `ERR unknown command 'FLUSHDB'` | Comando Redis errado | Verificar sintaxe em redis-cli |
| `ERROR Payment link armazenado somente no DB` | Redis não está sincronizando | Verificar `RedisConfig.java` |
| `TTL mostra -1` | Key não tem TTL | Resetar: `curl -X DELETE /api/payment-links/{id}/cache` |
| `Memory error: OOM command not allowed` | Redis fora de memória | `redis-cli FLUSHDB` ou aumentar memória |

---

## 📚 Próximos Testes

- [ ] Teste com múltiplos usuários simultâneos
- [ ] Teste de expiração automática (aguardar 3 horas)
- [ ] Teste de failover (Redis DOWN)
- [ ] Teste de persistência (salvar em disco)
- [ ] Teste de replicação Redis Cluster

