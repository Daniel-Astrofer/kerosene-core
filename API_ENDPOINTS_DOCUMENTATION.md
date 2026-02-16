# 📚 Documentação Completa de Endpoints - Kerosene API

**Data:** 11 de Fevereiro de 2026  
**Versão:** v0.5  
**Base URL:** `http://localhost:8080`

---

## 📋 Índice

1. [Autenticação](#autenticação)
2. [Wallet](#wallet)
3. [Ledger (Saldo)](#ledger-saldo)
4. [Transações Bitcoin](#transações-bitcoin)
5. [Headers Globais](#headers-globais)
6. [Status Codes](#status-codes)

---

## 🔐 Autenticação

### 1. Login
Autentica o usuário com username e passphrase. Retorna ID do usuário e JWT Token.

```http
POST /auth/login
Content-Type: application/json

{
  "username": "what",
  "passphrase": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
}
```

**Headers Requeridos:**
- `Content-Type: application/json`

**Response (202 Accepted):**
```
"1 eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJteV9kZXZpY2VfaGFzaCIsImlkIjoiMSIsImNsYWltcyI6eyJkZXZpY2VoYXNoIjoibXlfZGV2aWNlX2hhc2gifSwiaWF0IjoxNjM5MjM2ODAwLCJleHAiOjE2MzkyNDAwMDB9...."
```

**Formato Resposta:**
- `<USER_ID> <JWT_TOKEN>`

**Status Codes:**
- `202 Accepted` - Login bem-sucedido
- `401 Unauthorized` - Credenciais inválidas
- `400 Bad Request` - Username/passphrase inválidos

**Notas:**
- ✅ Saldo de 100.000 é adicionado automaticamente se for primeira vez
- ✅ JWT token renovado a cada login (valido por 24h)
- ✅ Token renovado automaticamente em cada requisição se faltar menos de 1 hora

---

### 2. Signup (Registrar)
Cria uma nova conta de usuário.

```http
POST /auth/signup
Content-Type: application/json

{
  "username": "newuser",
  "passphrase": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
}
```

**Headers Requeridos:**
- `Content-Type: application/json`

**Response (202 Accepted):**
```
"TOTP_SECRET_KEY_BASE64"
```

**Status Codes:**
- `202 Accepted` - Signup iniciado, aguardando TOTP
- `400 Bad Request` - Username já existe ou passphrase inválida

**Notas:**
- Retorna chave TOTP para verificação em duas etapas
- Passphrase deve ser válida BIP39 (12 ou 24 palavras)

---

### 3. Signup - Verificar TOTP
Verifica o código TOTP após o signup.

```http
POST /auth/signup/totp/verify
Content-Type: application/json
X-Device-Hash: "device_identification_hash"

{
  "username": "newuser",
  "passphrase": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
  "totpCode": "123456"
}
```

**Headers Requeridos:**
- `Content-Type: application/json`
- `X-Device-Hash: string` - Hash único do dispositivo

**Response (202 Accepted):**
```
"1 eyJhbGciOiJIUzI1NiJ9..."
```

**Status Codes:**
- `202 Accepted` - Conta criada com sucesso
- `400 Bad Request` - TOTP inválido
- `401 Unauthorized` - Credenciais incorretas

**Notas:**
- Device hash identifica o dispositivo para verificações futuras
- Recomenda-se usar hash SHA-256 do user-agent + IP

---

### 4. Login - Verificar TOTP
Verifica TOTP quando dispositivo não é reconhecido.

```http
POST /auth/login/totp/verify
Content-Type: application/json
X-Device-Hash: "device_hash_novo_ou_existente"

{
  "username": "what",
  "passphrase": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
  "totpCode": "123456"
}
```

**Headers Requeridos:**
- `Content-Type: application/json`
- `X-Device-Hash: string` - Hash do dispositivo

**Response (202 Accepted):**
```
"1 eyJhbGciOiJIUzI1NiJ9..."
```

**Status Codes:**
- `202 Accepted` - Login com TOTP bem-sucedido
- `400 Bad Request` - TOTP inválido ou expirado
- `401 Unauthorized` - Credenciais incorretas

---

## 💰 Wallet

Gerencia carteiras de criptografia do usuário.

### 1. Criar Carteira
Cria uma nova carteira Bitcoin para o usuário.

```http
POST /wallet/create
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "name": "Carteira Principal",
  "passphrase": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
}
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação
- `Content-Type: application/json`

**Response (201 Created):**
```
"wallet created"
```

**Status Codes:**
- `201 Created` - Carteira criada com sucesso
- `400 Bad Request` - Nome duplicado ou passphrase inválida
- `401 Unauthorized` - Token inválido/expirado

**Notas:**
- Nome da carteira deve ser único por usuário
- Passphrase é criptografada antes de armazenar

---

### 2. Listar Todas as Carteiras
Lista todas as carteiras do usuário autenticado.

```http
GET /wallet/all
Authorization: Bearer <JWT_TOKEN>
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "userId": 1,
    "name": "Carteira Principal",
    "address": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP"
  },
  {
    "id": 2,
    "userId": 1,
    "name": "Carteira Secundária",
    "address": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y"
  }
]
```

**Status Codes:**
- `200 OK` - Lista retornada com sucesso
- `401 Unauthorized` - Token inválido/expirado

---

### 3. Buscar Carteira por Nome
Busca uma carteira específica pelo nome.

```http
GET /wallet/find?name=Carteira%20Principal
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:**
- `name` (string) - Nome da carteira

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
{
  "id": 1,
  "userId": 1,
  "name": "Carteira Principal",
  "address": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP"
}
```

**Status Codes:**
- `200 OK` - Carteira encontrada
- `404 Not Found` - Carteira não existe
- `401 Unauthorized` - Token inválido/expirado

---

### 4. Atualizar Carteira
Renomeia uma carteira existente.

```http
PUT /wallet/update
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "name": "Carteira Principal",
  "newName": "Carteira Principal - Atualizada"
}
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação
- `Content-Type: application/json`

**Response (200 OK):**
```
"wallet updated"
```

**Status Codes:**
- `200 OK` - Carteira atualizada
- `400 Bad Request` - Novo nome duplicado
- `404 Not Found` - Carteira não encontrada
- `401 Unauthorized` - Token inválido/expirado

---

### 5. Deletar Carteira
Remove uma carteira do usuário.

```http
DELETE /wallet/delete
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "name": "Carteira Principal",
  "passphrase": "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
}
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação
- `Content-Type: application/json`

**Response (201 Created):**
```
"wallet deleted"
```

**Status Codes:**
- `201 Created` - Carteira deletada
- `404 Not Found` - Carteira não encontrada
- `401 Unauthorized` - Passphrase incorreta ou token inválido

---

## 📊 Ledger (Saldo)

Gerencia o livro-razão (ledger) e saldo das carteiras.

### 1. Listar Todos os Ledgers
Lista todos os ledgers (saldos) do usuário.

```http
GET /ledger/all
Authorization: Bearer <JWT_TOKEN>
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "walletId": 1,
    "walletName": "Carteira Principal",
    "balance": 100000,
    "nonce": 5,
    "lastHash": "a1b2c3d4e5f6...",
    "context": "TEST_INITIAL_BALANCE"
  },
  {
    "id": 2,
    "walletId": 2,
    "walletName": "Carteira Secundária",
    "balance": 50000,
    "nonce": 2,
    "lastHash": "f6e5d4c3b2a1...",
    "context": "DEPOSIT"
  }
]
```

**Status Codes:**
- `200 OK` - Ledgers retornados
- `401 Unauthorized` - Token inválido/expirado

---

### 2. Buscar Ledger por Nome da Carteira
Busca um ledger específico pelo nome da carteira.

```http
GET /ledger/find?walletName=Carteira%20Principal
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:**
- `walletName` (string) - Nome da carteira

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
{
  "id": 1,
  "walletId": 1,
  "walletName": "Carteira Principal",
  "balance": 100000,
  "nonce": 5,
  "lastHash": "a1b2c3d4e5f6...",
  "context": "TEST_INITIAL_BALANCE"
}
```

**Status Codes:**
- `200 OK` - Ledger encontrado
- `404 Not Found` - Carteira/ledger não encontrado
- `401 Unauthorized` - Token inválido ou carteira não pertence ao usuário

---

### 3. Consultar Saldo
Retorna o saldo de uma carteira específica.

```http
GET /ledger/balance?walletName=Carteira%20Principal
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:**
- `walletName` (string) - Nome da carteira

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```
100000
```

**Status Codes:**
- `200 OK` - Saldo retornado
- `404 Not Found` - Carteira não encontrada
- `401 Unauthorized` - Token inválido ou acesso negado

---

### 4. Processar Transação
Processa uma transação interna (transferência de saldo).

```http
POST /ledger/transaction
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 1000,
  "context": "TRANSFER"
}
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação
- `Content-Type: application/json`

**Response (202 Accepted):**
```json
(vazio)
```

**Status Codes:**
- `202 Accepted` - Transação processada
- `400 Bad Request` - Saldo insuficiente
- `404 Not Found` - Carteira não encontrada
- `401 Unauthorized` - Token inválido

---

### 5. Deletar Ledger
Remove o ledger de uma carteira.

```http
DELETE /ledger/delete?walletName=Carteira%20Principal
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:**
- `walletName` (string) - Nome da carteira

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```
"Ledger deleted successfully"
```

**Status Codes:**
- `200 OK` - Ledger deletado
- `404 Not Found` - Carteira/ledger não encontrado
- `401 Unauthorized` - Token inválido ou acesso negado

---

## ₿ Transações Bitcoin

Gerencia depósitos, saques e payment links em Bitcoin.

### Transações - Grupo 1: Estimativa e Status

#### 1. Estimar Taxa de Transação
Calcula as taxas estimadas para uma transação Bitcoin.

```http
GET /transactions/estimate-fee?amount=0.5
```

**Query Parameters:**
- `amount` (BigDecimal) - Valor em BTC

**Response (200 OK):**
```json
{
  "fastSatPerByte": 50,
  "standardSatPerByte": 35,
  "slowSatPerByte": 15,
  "estimatedFastBtc": 0.00115,
  "estimatedStandardBtc": 0.00078,
  "estimatedSlowBtc": 0.00034,
  "amountReceived": 0.49922,
  "totalToSend": 0.50078
}
```

**Status Codes:**
- `200 OK` - Estimativa calculada
- `400 Bad Request` - Valor inválido

**Notas:**
- Usa Mempool.space API para taxas em tempo real
- `amountReceived` = valor final após taxa (padrão: standard)
- `totalToSend` = valor total a enviar (incluindo taxa)

---

#### 2. Consultar Status de Transação
Verifica o status de uma transação na blockchain.

```http
GET /transactions/status?txid=abc123def456...
```

**Query Parameters:**
- `txid` (string) - Hash da transação

**Response (200 OK):**
```json
{
  "txid": "abc123def456...",
  "status": "confirmed",
  "feeSatoshis": 5600,
  "amountReceived": 0.495
}
```

**Status Codes:**
- `200 OK` - Status retornado
- `400 Bad Request` - TXID inválido
- `404 Not Found` - Transação não encontrada

---

### Transações - Grupo 2: Envio e Broadcast

#### 3. Enviar Transação
Envia uma transação Bitcoin já assinada.

```http
POST /transactions/send
Content-Type: application/json

{
  "fromAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "toAddress": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y",
  "amount": 0.5,
  "feeSatoshis": 5600
}
```

**Headers Requeridos:**
- `Content-Type: application/json`

**Response (202 Accepted):**
```json
{
  "txid": "abc123def456...",
  "status": "broadcasted",
  "feeSatoshis": 5600,
  "amountReceived": 0.495
}
```

**Status Codes:**
- `202 Accepted` - Transação enviada com sucesso
- `400 Bad Request` - Dados inválidos ou saldo insuficiente
- `500 Internal Server Error` - Erro ao comunicar com blockchain

---

#### 4. Broadcast de Transação Assinada
Transmite uma transação raw já assinada para a blockchain.

```http
POST /transactions/broadcast
Content-Type: application/json

{
  "rawTxHex": "0100000001abc123...def456"
}
```

**Headers Requeridos:**
- `Content-Type: application/json`

**Response (202 Accepted):**
```json
{
  "txid": "abc123def456...",
  "status": "broadcasted",
  "feeSatoshis": 0
}
```

**Status Codes:**
- `202 Accepted` - Transação transmitida
- `400 Bad Request` - Hex inválido
- `500 Internal Server Error` - Erro ao transmitir

---

### Transações - Grupo 3: Depósitos

#### 5. Obter Endereço de Depósito
Retorna o endereço Bitcoin central para depósitos.

```http
GET /transactions/deposit-address
```

**Response (200 OK):**
```
"1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP"
```

**Status Codes:**
- `200 OK` - Endereço retornado

**Notas:**
- Todos os depósitos devem ser enviados para este endereço
- Endereço configurável em `application.properties`

---

#### 6. Confirmar Depósito
Registra um novo depósito após validação na blockchain.

```http
POST /transactions/confirm-deposit
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "txid": "abc123def456...",
  "fromAddress": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y",
  "amount": 0.5
}
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação
- `Content-Type: application/json`

**Response (201 Created):**
```json
{
  "id": 1,
  "userId": 1,
  "txid": "abc123def456...",
  "fromAddress": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y",
  "toAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "amountBtc": 0.5,
  "confirmations": 1,
  "status": "confirmed",
  "createdAt": "2026-02-11T10:30:00",
  "confirmedAt": "2026-02-11T10:30:00"
}
```

**Status Codes:**
- `201 Created` - Depósito registrado
- `400 Bad Request` - TXID duplicado ou transação inválida
- `401 Unauthorized` - Token inválido/expirado

**Notas:**
- Valida TX na blockchain antes de registrar
- Status inicial é "confirmed"
- Será "credited" quando o saldo for sincronizado

---

#### 7. Listar Depósitos do Usuário
Lista todos os depósitos do usuário autenticado.

```http
GET /transactions/deposits
Authorization: Bearer <JWT_TOKEN>
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "userId": 1,
    "txid": "abc123def456...",
    "fromAddress": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y",
    "toAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
    "amountBtc": 0.5,
    "confirmations": 6,
    "status": "credited",
    "createdAt": "2026-02-11T09:30:00",
    "confirmedAt": "2026-02-11T09:35:00"
  }
]
```

**Status Codes:**
- `200 OK` - Lista retornada
- `401 Unauthorized` - Token inválido/expirado

---

#### 8. Consultar Saldo de Depósitos
Retorna o saldo total de depósitos creditados.

```http
GET /transactions/deposit-balance
Authorization: Bearer <JWT_TOKEN>
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```
1.5
```

**Status Codes:**
- `200 OK` - Saldo retornado
- `401 Unauthorized` - Token inválido/expirado

**Notas:**
- Retorna apenas saldo creditado (status = "credited")
- Formato: número em BTC

---

#### 9. Obter Depósito Específico
Retorna detalhes de um depósito específico.

```http
GET /transactions/deposit/abc123def456...
```

**Path Parameters:**
- `txid` (string) - Hash da transação do depósito

**Response (200 OK):**
```json
{
  "id": 1,
  "userId": 1,
  "txid": "abc123def456...",
  "fromAddress": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y",
  "toAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "amountBtc": 0.5,
  "confirmations": 6,
  "status": "credited",
  "createdAt": "2026-02-11T09:30:00",
  "confirmedAt": "2026-02-11T09:35:00"
}
```

**Status Codes:**
- `200 OK` - Depósito encontrado
- `404 Not Found` - Depósito não existe

---

### Transações - Grupo 4: Payment Links

#### 10. Criar Payment Link
Cria um novo payment link para receber pagamentos.

```http
POST /transactions/create-payment-link
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "amount": 0.25,
  "description": "Pagamento de serviço"
}
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação
- `Content-Type: application/json`

**Response (201 Created):**
```json
{
  "id": "pay_abc123xyz789",
  "userId": 1,
  "amountBtc": 0.25,
  "description": "Pagamento de serviço",
  "depositAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "status": "pending",
  "txid": null,
  "expiresAt": "2026-02-11T11:30:00",
  "createdAt": "2026-02-11T10:30:00",
  "paidAt": null,
  "completedAt": null
}
```

**Status Codes:**
- `201 Created` - Payment link criado
- `400 Bad Request` - Dados inválidos
- `401 Unauthorized` - Token inválido/expirado

**Notas:**
- Link expira em 60 minutos (configurável)
- ID único: `pay_<12 caracteres aleatórios>`
- Armazenado apenas em Redis (TTL: 3 horas)

---

#### 11. Obter Payment Link
Retorna informações de um payment link (público, sem autenticação).

```http
GET /transactions/payment-link/pay_abc123xyz789
```

**Path Parameters:**
- `linkId` (string) - ID do payment link

**Response (200 OK):**
```json
{
  "id": "pay_abc123xyz789",
  "userId": 1,
  "amountBtc": 0.25,
  "description": "Pagamento de serviço",
  "depositAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "status": "pending",
  "txid": null,
  "expiresAt": "2026-02-11T11:30:00",
  "createdAt": "2026-02-11T10:30:00",
  "paidAt": null,
  "completedAt": null
}
```

**Status Codes:**
- `200 OK` - Link encontrado
- `404 Not Found` - Link não existe ou expirou

**Notas:**
- Endpoint público (sem autenticação)
- Expiração verificada automaticamente

---

#### 12. Confirmar Pagamento de Payment Link
Marca um payment link como pago após validação na blockchain.

```http
POST /transactions/payment-link/pay_abc123xyz789/confirm
Content-Type: application/json

{
  "txid": "def456abc123...",
  "fromAddress": "3J98t1W1mU4Uxdg9BgKp8hPyV9Zf1z5C1Y"
}
```

**Path Parameters:**
- `linkId` (string) - ID do payment link

**Headers Requeridos:**
- `Content-Type: application/json`

**Response (200 OK):**
```json
{
  "id": "pay_abc123xyz789",
  "userId": 1,
  "amountBtc": 0.25,
  "description": "Pagamento de serviço",
  "depositAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "status": "paid",
  "txid": "def456abc123...",
  "expiresAt": "2026-02-11T11:30:00",
  "createdAt": "2026-02-11T10:30:00",
  "paidAt": "2026-02-11T10:35:00",
  "completedAt": null
}
```

**Status Codes:**
- `200 OK` - Pagamento confirmado
- `400 Bad Request` - Link já processado ou expirado
- `404 Not Found` - Link não existe
- `500 Internal Server Error` - Erro ao validar TX na blockchain

**Notas:**
- Valida TX na blockchain antes de confirmar
- Muda status: `pending` → `paid`
- Registra data/hora de pagamento

---

#### 13. Completar Payment Link
Marca um payment link como completado (libera o valor).

```http
POST /transactions/payment-link/pay_abc123xyz789/complete
Authorization: Bearer <JWT_TOKEN>
```

**Path Parameters:**
- `linkId` (string) - ID do payment link

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
{
  "id": "pay_abc123xyz789",
  "userId": 1,
  "amountBtc": 0.25,
  "description": "Pagamento de serviço",
  "depositAddress": "1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP",
  "status": "completed",
  "txid": "def456abc123...",
  "expiresAt": "2026-02-11T11:30:00",
  "createdAt": "2026-02-11T10:30:00",
  "paidAt": "2026-02-11T10:35:00",
  "completedAt": "2026-02-11T10:40:00"
}
```

**Status Codes:**
- `200 OK` - Payment link completado
- `400 Bad Request` - Link não está em status "paid"
- `404 Not Found` - Link não existe ou não pertence ao usuário
- `401 Unauthorized` - Token inválido/expirado

**Notas:**
- Só funciona se status = "paid"
- Requer autenticação (apenas dono pode completar)
- Muda status: `paid` → `completed`

---

#### 14. Listar Payment Links do Usuário
Lista todos os payment links criados pelo usuário.

```http
GET /transactions/payment-links
Authorization: Bearer <JWT_TOKEN>
```

**Headers Requeridos:**
- `Authorization: Bearer <JWT_TOKEN>` ⭐ Requer autenticação

**Response (200 OK):**
```json
[]
```

**Status Codes:**
- `200 OK` - Lista retornada (pode estar vazia)
- `401 Unauthorized` - Token inválido/expirado

**Notas:**
- Implementação atual retorna lista vazia
- TODO: Implementar índice dedicado no Redis

---

---

## 📌 Headers Globais

### Headers Requeridos (Autenticação)

Endpoints com ⭐ requerem estes headers:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
X-Device-Hash: "device_hash_ou_uuid"
```

| Header | Obrigatório | Descrição |
|--------|------------|-----------|
| `Authorization` | ⭐ SIM | JWT Token obtido no login |
| `X-Device-Hash` | Conditonal | Hash único do dispositivo (obrigatório em alguns endpoints) |
| `Content-Type` | Conditional | `application/json` para POST/PUT/DELETE |

### Headers de Resposta

```http
X-New-Token: eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
```

| Header | Descrição |
|--------|-----------|
| `X-New-Token` | JWT renovado (se token estava prestes a expirar) |
| `Content-Type` | Sempre `application/json` para respostas com body |

---

## 📊 Status Codes

| Code | Significado | Quando Usado |
|------|-------------|--------------|
| `200 OK` | Sucesso com dados | GET bem-sucedido, consultas |
| `201 Created` | Recurso criado | POST que cria novo recurso |
| `202 Accepted` | Solicitação aceita | Operações assíncronas |
| `400 Bad Request` | Dados inválidos | Validação falhou |
| `401 Unauthorized` | Não autenticado | Token ausente/inválido |
| `403 Forbidden` | Sem permissão | Usuário não tem acesso |
| `404 Not Found` | Recurso não existe | Entidade não encontrada |
| `409 Conflict` | Conflito | Recurso duplicado |
| `500 Internal Server Error` | Erro do servidor | Exceção não tratada |

---

## 🔑 Exemplos de Uso Completo

### Exemplo 1: Fluxo de Login e Deposit

```bash
# 1. Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"what","passphrase":"abandon abandon..."}'

# Response: "1 eyJhbGciOiJIUzI1NiJ9..."
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
DEVICE_HASH="abc123xyz"

# 2. Obter endereço de depósito
curl -X GET http://localhost:8080/transactions/deposit-address

# 3. Estimar taxa
curl -X GET "http://localhost:8080/transactions/estimate-fee?amount=0.5"

# 4. Confirmar depósito (após enviar BTC)
curl -X POST http://localhost:8080/transactions/confirm-deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "txid":"abc123...",
    "fromAddress":"3J98t1W...",
    "amount":0.5
  }'

# 5. Consultar saldo de depósitos
curl -X GET http://localhost:8080/transactions/deposit-balance \
  -H "Authorization: Bearer $TOKEN"
```

### Exemplo 2: Criar Payment Link

```bash
# 1. Criar payment link
curl -X POST http://localhost:8080/transactions/create-payment-link \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":0.25,"description":"Pagamento"}'

# Response: {"id":"pay_abc123xyz789",...}
LINK_ID="pay_abc123xyz789"

# 2. Compartilhar link com cliente
echo "http://localhost:8080/transactions/payment-link/$LINK_ID"

# 3. Cliente confirma pagamento (após enviar BTC)
curl -X POST "http://localhost:8080/transactions/payment-link/$LINK_ID/confirm" \
  -H "Content-Type: application/json" \
  -d '{"txid":"def456...","fromAddress":"3J98t1W..."}'

# 4. Receptor completa o pagamento
curl -X POST "http://localhost:8080/transactions/payment-link/$LINK_ID/complete" \
  -H "Authorization: Bearer $TOKEN"
```

### Exemplo 3: Operações com Wallet

```bash
# 1. Criar carteira
curl -X POST http://localhost:8080/wallet/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Minha Carteira","passphrase":"abandon abandon..."}'

# 2. Listar todas
curl -X GET http://localhost:8080/wallet/all \
  -H "Authorization: Bearer $TOKEN"

# 3. Consultar saldo
curl -X GET "http://localhost:8080/ledger/balance?walletName=Minha%20Carteira" \
  -H "Authorization: Bearer $TOKEN"

# 4. Deletar carteira
curl -X DELETE http://localhost:8080/wallet/delete \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Minha Carteira","passphrase":"abandon abandon..."}'
```

---

## 🚀 Versão e Data

- **Versão API:** v0.5
- **Data da Documentação:** 11 de Fevereiro de 2026
- **Última Atualização:** 11 de Fevereiro de 2026

---

## 📞 Suporte

Para dúvidas sobre os endpoints:
1. Consulte este documento
2. Verifique os READMEs nas pastas do projeto
3. Execute testes com Postman (arquivo incluído)

---
