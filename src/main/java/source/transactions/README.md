# Transactions Module

Módulo responsável por gerenciar **transações Bitcoin**, **depósitos** e **payment links** na aplicação Kerosene. Este módulo interage com a blockchain via API do Blockchain.info para validar transações e manter o sistema sincronizado com a rede Bitcoin.

---

## 📋 Estrutura da Pasta

```
transactions/
├── controller/           # Endpoints REST da API
├── dto/                 # Data Transfer Objects
├── infra/               # Integrações externas
├── model/               # Entidades JPA/Hibernate
├── repository/          # Acesso a dados (Database)
├── service/             # Lógica de negócio
└── README.md           # Esta documentação
```

---

## 🎯 Componentes Principais

### 1. **Controllers**

#### `TransactionController` (`controller/TransactionController.java`)
**Responsabilidade:** Orquestra todas as operações de transação, depósito e payment links.

**Endpoints principais:**
- **`POST /transactions/send`** - Envia uma transação Bitcoin assinada
- **`GET /transactions/status`** - Consulta o status de uma transação
- **`GET /transactions/estimate-fee`** - Calcula taxas de transação estimadas
- **`POST /transactions/broadcast`** - Broadcast de transação assinada raw

**Endpoints de Depósito:**
- **`GET /transactions/deposit-address`** - Retorna o endereço de depósito do servidor
- **`POST /transactions/confirm-deposit`** - Registra um novo depósito após validação
- **`GET /transactions/deposits`** - Lista depósitos do usuário autenticado
- **`GET /transactions/deposit-balance`** - Saldo total de depósitos confirmados
- **`GET /transactions/deposit/{txid}`** - Detalhes de um depósito específico

**Endpoints de Payment Link:**
- **`POST /transactions/create-payment-link`** - Cria um novo payment link
- **`GET /transactions/payment-link/{linkId}`** - Obtém detalhes do payment link
- **`POST /transactions/payment-link/{linkId}/confirm`** - Confirma pagamento após validação de TX
- **`POST /transactions/payment-link/{linkId}/complete`** - Libera o valor para o usuário
- **`GET /transactions/payment-links`** - Lista payment links do usuário

#### `PaymentLinkController` (`controller/PaymentLinkController.java`)
**⚠️ REDUNDANTE!** Este controller duplica a funcionalidade de `TransactionController`.

**Endpoints duplicados:**
- `POST /api/payment-links` → duplica `/transactions/create-payment-link`
- `GET /api/payment-links/{linkId}` → duplica `/transactions/payment-link/{linkId}`
- `POST /api/payment-links/{linkId}/confirm` → duplica `/transactions/payment-link/{linkId}/confirm`
- `POST /api/payment-links/{linkId}/complete` → duplica `/transactions/payment-link/{linkId}/complete`

**Ação necessária:** Manter apenas `TransactionController` e remover `PaymentLinkController`.

---

### 2. **Services**

#### `TransactionService` (interface)
```java
public interface TransactionService {
    TransactionResponseDTO sendTransaction(TransactionRequestDTO request);
    TransactionResponseDTO getStatus(String txid);
    TransactionResponseDTO broadcastSignedTransaction(SignedTransactionDTO signedTx);
    EstimatedFeeDTO estimateFee(BigDecimal amount);
}
```

#### `TransactionServiceImpl` (`service/TransactionServiceImpl.java`)
**Responsabilidade:** Implementa lógica de transações Bitcoin.

**Funcionalidades principais:**
- **`estimateFee()`** - Consulta taxas recomendadas via Mempool.space API
  - Calcula taxas para 3 velocidades: Fast, Standard, Slow
  - Baseado em tamanho médio de TX (225 bytes)
- **`sendTransaction()`** - Envia transação já assinada para blockchain
  - Descontar taxas do valor total
  - Chamar `BlockchainInfoClient` para broadcast
- **`getStatus()`** - Consulta status da TX (confirmada/não confirmada)
- **`broadcastSignedTransaction()`** - Broadcast de TX raw já assinada

**Dependências:**
- `BlockchainInfoClient` - Cliente HTTP para Blockchain.info API

---

#### `DepositService` (`service/DepositService.java`)
**Responsabilidade:** Gerencia depósitos de Bitcoin dos usuários.

**Funcionalidades principais:**
- **`getDepositAddress()`** - Retorna endereço central de depósitos do servidor
- **`confirmDeposit()`** - Registra novo depósito após validação
  - Verifica se TXID já foi registrado (evita duplicatas)
  - Valida TX na blockchain (endereço e valor corretos)
  - Persiste em banco de dados
- **`getUserDeposits()`** - Lista todos os depósitos de um usuário
- **`getUserDepositBalance()`** - Calcula saldo de depósitos creditados
- **`getDepositByTxid()`** - Busca depósito específico
- **`creditDeposit()`** - Marca depósito como creditado

**Fluxo de Depósito:**
1. Usuário envia Bitcoin para `serverDepositAddress`
2. Cliente API chama `/transactions/confirm-deposit` com TXID
3. Sistema valida TX na blockchain
4. Se válido, persiste em banco de dados com status `confirmed`
5. Administrador marca como `credited` quando sincronizar saldo

**Entidade:** `DepositEntity`
- Campos: `id`, `userId`, `txid`, `fromAddress`, `toAddress`, `amountBtc`, `confirmations`, `status`, `createdAt`, `confirmedAt`
- Tabela: `deposits`
- Status: pending → confirmed → credited

---

#### `PaymentLinkService` (`service/PaymentLinkService.java`)
**Responsabilidade:** Gerencia payment links (links de pagamento com expiração).

**Funcionalidades principais:**
- **`createPaymentLink()`** - Cria novo link com expiração (padrão 60 min)
  - Gera ID único (`pay_<UUID>`)
  - Armazena em **Redis** (não em DB!)
  - Dados persistem por 3 horas no Redis
- **`getPaymentLink()`** - Recupera link do Redis
  - Verifica expiração automática
  - Retorna `null` se não encontrado
- **`confirmPayment()`** - Marca como pago após validação de TX
  - Valida TXID na blockchain
  - Muda status: `pending` → `paid`
  - Registra hora de pagamento
- **`completePayment()`** - Libera o valor para o usuário
  - Muda status: `paid` → `completed`
  - Apenas para links já pagos
- **`getUserPaymentLinks()`** - Lista links do usuário (do Redis)
- **`removeFromRedis()`** - Remove link do cache

**Armazenamento:**
- Redis é a ÚNICA fonte de dados para payment links
- Estrutura: `payment_link:<linkId>` → `PaymentLinkDTO` (serializado JSON)
- Índice: `user_payment_links:<userId>` → Set de linkIds
- TTL: 3 horas no Redis

**Fluxo de Payment Link:**
1. Usuário cria payment link para receber pagamento
2. Link fica disponível por 60 minutos (configurável)
3. Pagador envia Bitcoin para `depositAddress` do link
4. Receptor chama `/confirm-payment` com TXID
5. Sistema valida e marca como `paid`
6. Receptor chama `/complete-payment` para liberar o valor
7. Link expira ou é removido após 3 horas

---

### 3. **Data Models**

#### `DepositEntity` (`model/DepositEntity.java`)
Representa um depósito de Bitcoin no banco de dados.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | ID primária (AUTO_INCREMENT) |
| `userId` | Long | ID do usuário que fez depósito |
| `txid` | String | Hash da transação (UNIQUE) |
| `fromAddress` | String | Endereço que enviou Bitcoin |
| `toAddress` | String | Endereço que recebeu (servidor) |
| `amountBtc` | BigDecimal | Valor em BTC |
| `confirmations` | Long | Número de confirmações blockchain |
| `status` | String | `pending` \| `confirmed` \| `credited` |
| `createdAt` | LocalDateTime | Data de criação |
| `confirmedAt` | LocalDateTime | Data de confirmação |

**Índices:**
- `txid` (UNIQUE) - previne depósitos duplicados

---

#### `PaymentLinkEntity` (`model/PaymentLinkEntity.java`)
**⚠️ NÃO UTILIZADO!** Payment links são armazenados apenas em Redis.

**Remover:** Esta entidade é código morto e nunca é usada pela aplicação.

---

### 4. **DTOs (Data Transfer Objects)**

#### `TransactionRequestDTO`
```java
{
  "fromAddress": "string",
  "toAddress": "string",
  "amount": BigDecimal,
  "feeSatoshis": Long
}
```

#### `TransactionResponseDTO`
```java
{
  "txid": "string",           // Hash da transação
  "status": "string",         // "broadcasted", "confirmed", "error"
  "feeSatoshis": Long,        // Taxa paga
  "amountReceived": BigDecimal // Valor que chegou
}
```

#### `EstimatedFeeDTO`
```java
{
  "fastSatPerByte": Long,
  "standardSatPerByte": Long,
  "slowSatPerByte": Long,
  "estimatedFastBtc": BigDecimal,
  "estimatedStandardBtc": BigDecimal,
  "estimatedSlowBtc": BigDecimal,
  "amountReceived": BigDecimal,
  "totalToSend": BigDecimal
}
```

#### `SignedTransactionDTO`
```java
{
  "rawTxHex": "string"  // Transação assinada em formato hex
}
```

#### `DepositDTO`
```java
{
  "id": Long,
  "userId": Long,
  "txid": "string",
  "fromAddress": "string",
  "toAddress": "string",
  "amountBtc": BigDecimal,
  "confirmations": Long,
  "status": "string",           // pending | confirmed | credited
  "createdAt": LocalDateTime,
  "confirmedAt": LocalDateTime
}
```

#### `PaymentLinkDTO`
```java
{
  "id": "string",               // pay_<UUID>
  "userId": Long,
  "amountBtc": BigDecimal,
  "description": "string",
  "depositAddress": "string",
  "status": "string",           // pending | paid | expired | completed
  "txid": "string",             // null até pagamento
  "expiresAt": LocalDateTime,
  "createdAt": LocalDateTime,
  "paidAt": LocalDateTime,
  "completedAt": LocalDateTime
}
```

---

### 5. **Infraestrutura**

#### `BlockchainInfoClient` (`infra/BlockchainInfoClient.java`)
**Responsabilidade:** Cliente HTTP para integração com Blockchain.info e Mempool.space API.

**Dependências externas:**
- API Blockchain.info (https://blockchain.info)
- API Mempool.space (https://mempool.space) - para taxas

**Métodos principais:**
- **`getTransactionInfo()`** - Consulta dados da TX na blockchain
- **`getAddressBalance()`** - Saldo de um endereço Bitcoin
- **`validateDepositTransaction()`** - Valida se TX chegou ao endereço correto
  - Modo REAL: consulta blockchain
  - Modo MOCK: validação simplificada (ativado com `bitcoin.mock-mode=true`)
- **`getRecommendedFees()`** - Obtém taxas recomendadas do Mempool.space
- **`pushSignedTransaction()`** - Envia TX assinada para blockchain
- **`sendTransaction()`** - Envia TX (requer hex assinado)

**Modo Mock:**
- Ativado via propriedade `bitcoin.mock-mode=true`
- Permite testar sem Bitcoin real
- Retorna respostas simuladas

---

#### `RedisConfig` (`infra/RedisConfig.java`)
**Responsabilidade:** Configuração do Spring Data Redis para serialização.

**Configurações:**
- Serializa `PaymentLinkDTO` como JSON (Jackson2JsonRedisSerializer)
- Suporte a `LocalDateTime` (JavaTimeModule)
- Chaves e valores em String/JSON

---

### 6. **Repositories**

#### `DepositRepository`
Interface JPA para acesso a `DepositEntity`.

```java
List<DepositEntity> findByUserId(Long userId);
Optional<DepositEntity> findByTxid(String txid);
```

#### `PaymentLinkRepository`
**⚠️ NÃO UTILIZADO!** Payment links usam Redis, não banco de dados.

**Remover:** Código morto.

---

## 🔄 Fluxos de Negócio

### Fluxo 1: Depositar Bitcoin

```
1. Usuário envia BTC para: 1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP
2. Aguarda TX ser confirmada na blockchain
3. Chama API: POST /transactions/confirm-deposit
   {
     "txid": "abc123...",
     "fromAddress": "3J98t1W...",
     "amount": 0.5
   }
4. Sistema valida na blockchain
5. Persiste como DepositEntity (status="confirmed")
6. Usuário recebe notificação
7. Admin marca como "credited" quando sincronizar saldo
```

### Fluxo 2: Payment Link (Receber Pagamento)

```
1. Receptor chama: POST /transactions/create-payment-link
   {
     "amount": 0.25,
     "description": "Pagamento de serviço"
   }
2. Sistema cria link (id=pay_abc123) no Redis
3. Link expira em 60 min (configurável)
4. Receptor compartilha: /transactions/payment-link/pay_abc123
5. Pagador acessa link e vê endereço para enviar
6. Pagador envia BTC
7. Receptor chama: POST /transactions/payment-link/pay_abc123/confirm
   {
     "txid": "def456...",
     "fromAddress": "..."
   }
8. Sistema valida TX
9. Status muda para "paid"
10. Receptor chama: POST /transactions/payment-link/pay_abc123/complete
11. Link liberado, status="completed"
```

### Fluxo 3: Estimar Taxas

```
1. Cliente chama: GET /transactions/estimate-fee?amount=0.5
2. Sistema consulta Mempool.space para taxas atuais
3. Calcula 3 cenários (Fast, Standard, Slow)
4. Retorna:
   {
     "estimatedFastBtc": 0.00115,
     "estimatedStandardBtc": 0.00078,
     "estimatedSlowBtc": 0.00034,
     "amountReceived": 0.49922,
     "totalToSend": 0.50078
   }
```

---

## ⚙️ Configurações (application.properties)

```properties
# Endereço Bitcoin para depósitos
bitcoin.deposit-address=1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP

# Confirmações mínimas para validar depósito
bitcoin.min-confirmations=1

# Expiração de payment links (minutos)
bitcoin.payment-link-expiration-minutes=60

# API key Blockchain.info (deixar em branco se não tiver)
blockchain.info.api-key=

# Ativar modo mock (testa sem BTC real)
bitcoin.mock-mode=true

# Redis
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
```

---

## 🐛 Código Morto / Redundâncias

### 1. **`BitcoinRpcClient` - REMOVER**
- Arquivo: `infra/BitcoinRpcClient.java`
- Status: **Não utilizado em nenhum lugar**
- Razão: Sistema usa apenas `BlockchainInfoClient`
- Ação: Deletar arquivo

### 2. **`PaymentLinkController` - REMOVER**
- Arquivo: `controller/PaymentLinkController.java`
- Status: **Duplica `TransactionController`**
- Razão: Mesma funcionalidade com endpoints diferentes
- Ação: Manter apenas `TransactionController`, deletar este

### 3. **`PaymentLinkEntity` - REMOVER**
- Arquivo: `model/PaymentLinkEntity.java`
- Status: **Nunca é usado**
- Razão: Payment links são 100% em Redis
- Ação: Deletar arquivo e JPA mappings

### 4. **`PaymentLinkRepository` - REMOVER**
- Arquivo: `repository/PaymentLinkRepository.java`
- Status: **Nunca é usado**
- Razão: Payment links não usam banco de dados
- Ação: Deletar arquivo

---

## 📊 Dependências

| Dependência | Usado por | Versão |
|-------------|-----------|--------|
| Spring Web | Controllers | Spring Boot |
| Spring Data JPA | DepositService | Spring Boot |
| Spring Data Redis | PaymentLinkService | Spring Boot |
| Jackson | Serialização JSON | Jackson 2 |
| RestTemplate | BlockchainInfoClient | Spring Framework |

---

## 🚀 Exemplos de Uso

### Exemplo 1: Depositar Bitcoin

```bash
# 1. Obter endereço
curl -X GET http://localhost:8080/transactions/deposit-address

# 2. Enviar BTC para esse endereço (fazer offline)

# 3. Confirmar depósito
curl -X POST http://localhost:8080/transactions/confirm-deposit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "txid": "abc123...",
    "fromAddress": "3J98t1W...",
    "amount": 0.5
  }'

# 4. Consultar depósitos
curl -X GET http://localhost:8080/transactions/deposits \
  -H "Authorization: Bearer <token>"
```

### Exemplo 2: Criar Payment Link

```bash
# 1. Criar link
curl -X POST http://localhost:8080/transactions/create-payment-link \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 0.25,
    "description": "Pagamento de serviço"
  }'

# Retorna: { "id": "pay_abc123", ... }

# 2. Compartilhar: http://seu-app.com/transactions/payment-link/pay_abc123

# 3. Após receber BTC, confirmar pagamento
curl -X POST http://localhost:8080/transactions/payment-link/pay_abc123/confirm \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "txid": "def456...",
    "fromAddress": "..."
  }'

# 4. Liberar pagamento
curl -X POST http://localhost:8080/transactions/payment-link/pay_abc123/complete \
  -H "Authorization: Bearer <token>"
```

---

## 📝 Notas Importantes

1. **Bitcoin é MOCK por padrão** - Configure `bitcoin.mock-mode=false` para uso real
2. **Payment Links no Redis** - Dados desaparecem se Redis cair (sem persistência)
3. **Depósitos no Banco** - Persistem indefinidamente
4. **Validação de TX** - Sempre consulta blockchain antes de salvar
5. **Autenticação** - Todos endpoints (exceto depósito) requerem token JWT
6. **Taxas de Bitcoin** - Consultadas em tempo real do Mempool.space

---

## 🔍 Testes

**Casos de teste recomendados:**

1. Criar payment link com expiração
2. Confirmar pagamento com TXID inválido
3. Tentar confirmar pagamento no link expirado
4. Depositar valor zero (deve falhar)
5. Consultar depósito de outro usuário (deve falhar)
6. Estimar taxas com valor negativo (deve falhar)

---

## 👤 Autor

Sistema de Transações Bitcoin - Kerosene v0.5

---

## 📞 Suporte

Para dúvidas sobre este módulo, consulte:
- Documentação de Bitcoin: https://developer.bitcoin.org/
- Blockchain.info API: https://www.blockchain.com/api
- Mempool.space API: https://mempool.space/api
