# Kerosene Backend API Endpoints Documentation

Abaixo estão listados **TODOS** os endpoints com seus paramêtros exatos. 
Todas as respostas (Response Body) demonstram **exatamente o que é retornado no corpo do JSON da requisição**, com a casca do formato padrão `ApiResponse`.

---

## 0. Account Security Modes

O campo `accountSecurity` é escolhido uma única vez no momento do cadastro (`POST /auth/signup`) e define como a plataforma co-assina operações da conta. **Não pode ser alterado após o onboarding.**

| Valor | Descrição | Servidor guarda |
|---|---|---|
| `STANDARD` | Senha + TOTP (comportamento padrão) | Nada extra |
| `SHAMIR` | Shamirs Secret Sharing — a chave privada do usuário é dividida em N shares; a plataforma guarda 1 share criptografado (AES-256-GCM) e age como co-assinante obrigatório | 1 share criptografado em `platform_cosigner_secret` |
| `MULTISIG_2FA` | A plataforma guarda uma chave de co-assinatura criptografada (AES-256-GCM); toda operação sensível requer a autenticação do usuário **e** a assinatura da plataforma | 1 chave de co-assinatura criptografada em `platform_cosigner_secret` |

> **Segurança**: o campo `platform_cosigner_secret` é armazenado como [ciphertext AES-256-GCM] Base64 no banco de dados, nunca como texto plano. A chave de criptografia é o próprio `AES_SECRET` já configurado na aplicação. **Este campo NUNCA é retornado em nenhuma resposta de API** (`@JsonIgnore` na entidade).

---

## 1. Authentication & Users (`/auth`)

### 1.1 Login Initiation
- **URL**: `/auth/login`
- **Method**: `POST`
- **Request Body**:
```json
{
  "username": "user123",
  "passphrase": "mypassword"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Login requires TOTP verification.",
  "data": {
    "authId": "temp-auth-id-12345"
  }
}
```
- **Error Responses** (Exemplo de formato em caso de falha):
```json
{
  "success": false,
  "message": "No account found matching the provided username.",
  "errorCode": "ERR_AUTH_USER_NOT_FOUND"
}
```

### 1.2 User Registration (Signup)
- **URL**: `/auth/signup`
- **Method**: `POST`
- **Request Body**:
```json
{
  "username": "newuser",
  "passphrase": "abandon ability able about above absent...",
  "challenge": "random-challenge-string",
  "nonce": "123456",
  "accountSecurity": "STANDARD"
}
```

> **`accountSecurity`** — opcional. Valores aceitos: `STANDARD` (padrão), `SHAMIR`, `MULTISIG_2FA`. Ver seção 0.

> **BIP39 Multilingual**: o campo `passphrase` aceita frases mnemônicas válidas em **Inglês (EN)** ou **Português do Brasil (PT-BR)**. A frase deve ter 12, 15, 18, 21 ou 24 palavras da wordlist BIP39 oficial. O sistema obriga a resolução do desafio PoW para prosseguir.

- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "User registered. Please configure your authenticator app using the provided setup key.",
  "data": {
    "totpSecret": "JBSWY3DPEHPK3PXP",
    "qrCodeUri": "otpauth://totp/Kerosene:newuser?secret=..."
  }
}
> **Observação de Inatividade:** O usuário é criado com `isActive = false` e precisará pagar a taxa de Onboarding (100 BRL) usando `/voucher/onboarding-link` após a validação do TOTP, antes de realizar operações de ledger.
```
- **Error Responses**:

| `errorCode` | Motivo |
|---|---|
| `ERR_POW_INVALID` | O desafio/nonce PoW falhou ou expirou |
| `ERR_PASSPHRASE_INVALID_WORD` | Uma ou mais palavras da frase não existem na wordlist EN nem PT-BR |
| `ERR_PASSPHRASE_INVALID_LENGTH` | Número de palavras inválido (deve ser 12/15/18/21/24) |
| `ERR_PASSPHRASE_INVALID` | Checksum BIP39 inválido (frase corrompida ou em ordem errada) |
| `ERR_USERNAME_ALREADY_EXISTS` | Username já cadastrado |

### 1.3 Signup TOTP Verification
- **URL**: `/auth/signup/totp/verify`
- **Method**: `POST`

- **Request Body**:
```json
{
  "username": "newuser",
  "totpCode": "123456"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Device verified and account successfully created. You are now fully authenticated.",
  "data": "a1b2c3d4e5f6g7h8..." // Session ID for Onboarding
}
```
> **Atenção:** Como a persistência neste momento é temporária no Redis, a API não retorna um JWT, mas sim um `sessionId` que deve ser usado nos passos seguintes (Registro de Passkey e Geração do Voucher de Onboarding).

### 1.4 Login TOTP Verification
- **URL**: `/auth/login/totp/verify`
- **Method**: `POST`

- **Request Body**:
```json
{
  "username": "user123",
  "totpCode": "123456"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Authentication successful.",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // O JWT Token final do login
}
```

### 1.5 Proof of Work (PoW) Challenge
- **URL**: `/auth/pow/challenge`
- **Method**: `GET`
- **Description**: Retorna um desafio único que o cliente deve resolver com um *nonce* (`SHA-256(challenge + nonce)` terminando/começando com zeros dependendo da dificuldade) para permitir registros, prevenindo spam bots.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "PoW Challenge generated",
  "data": {
    "challenge": "ab82c9f1a23b..."
  }
}
```

### 1.6 Passkey Registration Start
- **URL**: `/auth/passkey/register/start`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Description**: Inicia o fluxo de registro de uma Passkey (WebAuthn). Requer que o usuário esteja logado via TOTP padrão.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Registration options generated",
  "data": "{ PublicKeyCredentialCreationOptions JSON String }"
}
```

### 1.7 Passkey Registration Finish
- **URL**: `/auth/passkey/register/finish`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**: Raw JSON string retornada via cliente do Authenticator.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Passkey registered successfully",
  "data": "OK"
}
```

### 1.8 Passkey Login Start
- **URL**: `/auth/passkey/login/start?username={username}`
- **Method**: `POST`
- **Description**: Inicia o fluxo de login via Passkey caso o usuário já tenha registrado uma. Retorna o json de request de assertion.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Login options generated",
  "data": "{ AssertionRequest JSON String }"
}
```

### 1.9 Passkey Login Finish
- **URL**: `/auth/passkey/login/finish?username={username}`
- **Method**: `POST`
- **Request Body**: Raw JSON string retornada via assertion do Authenticator.
- **Description**: Termina fluxo Passkey e ao comprovar, devolve o Token JWT, **bypassando** a etapa de TOTP.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Passkey login successful",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // JWT Token
}
```

### 1.10 Passkey Onboarding Registration Start
- **URL**: `/auth/passkey/register/onboarding/start?sessionId={sessionId}`
- **Method**: `POST`
- **Description**: Inicia o fluxo de registro de uma Passkey vinculado a um `sessionId` temporário, antes de o usuário existir no PostgreSQL.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Onboarding passkey options generated",
  "data": "{ PublicKeyCredentialCreationOptions JSON String }"
}
```

### 1.11 Passkey Onboarding Registration Finish
- **URL**: `/auth/passkey/register/onboarding/finish?sessionId={sessionId}`
- **Method**: `POST`
- **Request Body**: Raw JSON string retornada via cliente do Authenticator.
- **Description**: Salva a Passkey temporariamente no `SignupState` do Redis. A inserção no DB ocorrerá apenas após 3 confirmações do voucher Onboarding.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Passkey attached to Onboarding Session",
  "data": "OK"
}
```

### 1.12 Device Change / Passkey Recovery Flow (Lost Passkey)
O sistema não bloqueia múltiplos dispositivos ou Passkeys vinculados à mesma conta (as verificações baseadas em `X-Device-Hash` foram removidas em favor da segurança descentralizada do Tor). Se o usuário perder acesso ao seu YubiKey ou dispositivo principal contendo a Passkey, o fluxo de recuperação exigirá autenticação bruta.

**Passos para Cadastrar Novo Dispositivo/Passkey:**
1. O Front-End deve tentar executar a biometria/passkey, que retornará falha no client-side (credential not found).
2. O usuário faz fallback enviando Username + Passphrase para **`POST /auth/login`**.
3. Em seguida, envia o código 2FA do aplicativo Google/Microsoft para **`POST /auth/login/totp/verify`**.
4. O backend emitirá um JWT Token **válido**. 
5. Logado na conta, o usuário envia os headers de autenticação JWT e executa `/auth/passkey/register/start` (passo 1.6) e `/auth/passkey/register/finish` (passo 1.7) para atrelar a nova Passkey do seu novo celular/device à conta existente no PostgeSQL.

---

## 2. Wallet Management (`/wallet`)

### 2.1 Create Wallet
- **URL**: `/wallet/create`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "minhacarteira",
  "passphrase": "palavras semente bip trinta e nove..."
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Wallet created successfully",
  "data": "Wallet created successfully"
}
```

### 2.2 Get All Wallets
- **URL**: `/wallet/all`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "name": "minhacarteira",
      "passphraseHash": "$2a$10$hashed...",
      "createdAt": "2023-10-01T12:00:00",
      "updatedAt": "2023-10-01T12:00:00",
      "isActive": true
    }
  ]
}
```

### 2.3 Find Wallet By Name
- **URL**: `/wallet/find?name=minhacarteira`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 1,
    "name": "minhacarteira",
    "passphraseHash": "$2a$10$hashed...",
    "createdAt": "2023-10-01T12:00:00",
    "updatedAt": null,
    "isActive": true
  }
}
```

### 2.4 Update Wallet
- **URL**: `/wallet/update`
- **Method**: `PUT`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "minhacarteira",
  "newName": "carteira_investimento",
  "passphrase": "senha_atual_para_validar"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Wallet name updated successfully.",
  "data": "Wallet name updated successfully."
}
```

### 2.5 Delete Wallet
- **URL**: `/wallet/delete`
- **Method**: `DELETE`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "carteira_investimento",
  "passphrase": "senha_atual_para_validar"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Wallet deleted successfully.",
  "data": "Wallet deleted successfully."
}
```

---

## 3. Ledger & Internal Financials (`/ledger`)

### 3.1 Process Internal Transaction
- **URL**: `/ledger/transaction`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "sender": "minha_carteira", 
  "receiver": "username_do_amigo ou endereco_btc_recebedor",
  "amount": 0.05123,
  "context": "Pagamento do jantar"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Transaction processed successfully.",
  "data": {
    "sender": "minha_carteira",
    "receiver": "amigo_username",
    "amount": 0.05123,
    "context": "Pagamento do jantar"
  }
}
```

### 3.2 Get Transaction History
- **URL**: `/ledger/history`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "uuid-da-transacao",
      "senderIdentifier": "minha_carteira",
      "senderUserId": 1,
      "receiverIdentifier": "amigo_username",
      "receiverUserId": 2,
      "transactionType": "INTERNAL",
      "amount": 0.05000000,
      "status": "CONCLUDED",
      "networkFee": null,
      "blockchainTxid": null,
      "context": "Pagamento do jantar",
      "createdAt": "2023-10-01T15:00:00"
    }
  ]
}
```

### 3.3 Get All Ledgers
- **URL**: `/ledger/all`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "walletId": 1,
      "balance": 1.25000000
    }
  ]
}
```

### 3.4 Find Ledger By Wallet Name
- **URL**: `/ledger/find?walletName=minhacarteira`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 1,
    "walletId": 1,
    "balance": 1.25000000
  }
}
```

### 3.5 Get Balance
- **URL**: `/ledger/balance?walletName=minhacarteira`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": 1.25000000
}
```

### 3.6 Create Internal Payment Request
- **URL**: `/ledger/payment-request`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "amount": 0.0125,
  "receiverWalletName": "minha_carteira"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Payment request created.",
  "data": {
    "id": "link-uuid-1234",
    "requesterUserId": 1,
    "receiverWalletName": "minha_carteira",
    "amount": 0.0125,
    "status": "PENDING",
    "expiresAt": "2023-10-01T18:00:00",
    "createdAt": "2023-10-01T17:00:00",
    "paidAt": null
  }
}
```

> **WebSocket — Notificação em Tempo Real**: Após criar o link, o criador deve conectar-se e assinar `/topic/payment-request/{id}`. Quando o pagador executar o endpoint 3.8 com sucesso, o backend publica imediatamente o DTO completo (com `status: PAID`) neste tópico — sem necessidade de polling.
>
> Conexão: `ws://host/ws/payment-request?token=<jwt>` (SockJS) ou `ws://host/ws/raw-payment-request?token=<jwt>` (raw)

### 3.7 Retrieve Payment Request
- **URL**: `GET /ledger/payment-request/{linkId}`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "link-uuid-1234",
    "requesterUserId": 1,
    "receiverWalletName": "minha_carteira",
    "amount": 0.0125,
    "status": "PENDING",
    "expiresAt": "2023-10-01T18:00:00",
    "createdAt": "2023-10-01T17:00:00",
    "paidAt": null
  }
}
```

### 3.8 Pay Payment Request
- **URL**: `POST /ledger/payment-request/{linkId}/pay`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "payerWalletName": "carteira_pagadora"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Payment successful.",
  "data": {
    "id": "link-uuid-1234",
    "requesterUserId": 1,
    "receiverWalletName": "minha_carteira",
    "amount": 0.0125,
    "status": "PAID",
    "expiresAt": "2023-10-01T18:00:00",
    "createdAt": "2023-10-01T17:00:00",
    "paidAt": "2023-10-01T17:30:00"
  }
}
```
> **Efeito colateral**: Além de concluir a transação interna, este endpoint dispara um push WebSocket para `/topic/payment-request/{linkId}` com o DTO atualizado (`status: PAID`) — notificando o criador do link em tempo real.

---

## 4. On-Chain Bitcoin Transactions (`/transactions`)

### 4.1 Get Global Deposit Address
- **URL**: `/transactions/deposit-address`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
}
```

### 4.2 Estimate Network Fee
- **URL**: `/transactions/estimate-fee?amount=1.5`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "fastSatoshisPerByte": 30,
    "standardSatoshisPerByte": 15,
    "slowSatoshisPerByte": 5,
    "estimatedFastBtc": 0.00008500,
    "estimatedStandardBtc": 0.00004250,
    "estimatedSlowBtc": 0.00001400,
    "amountReceived": 1.5,
    "totalToSend": 1.50004250
  }
}
```

### 4.3 Create Unsigned Transaction
- **URL**: `/transactions/create-unsigned`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "toAddress": "bc1...",
  "amount": 0.5,
  "feeLevel": "fast" 
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Unsigned transaction created",
  "data": {
    "rawTxHex": "0100000001...",
    "txId": "sha256-hash",
    "inputs": [],
    "outputs": [],
    "totalAmount": 0.5,
    "fee": 5000,
    "fromAddress": "bc1...",
    "toAddress": "bc1..."
  }
}
```

### 4.4 Broadcast Signed Transaction
- **URL**: `/transactions/broadcast`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "signedTxHex": "0100000001tx1234..."
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Transaction broadcasted to mempool",
  "data": {
    "txid": "hash-da-transacao",
    "status": "broadcasted",
    "feeSatoshis": 5000,
    "amountReceived": 0.5
  }
}
```

### 4.5 On-Chain Withdrawal
- **URL**: `/transactions/withdraw`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "fromWalletName": "minhacarteira",
  "toAddress": "bc1_destino...",
  "amount": 0.1,
  "description": "Retirada externa"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Withdrawal sent to blockchain",
  "data": {
    "txid": "hash-on-chain",
    "status": "broadcasted",
    "feeSatoshis": 3000,
    "amountReceived": 0.10000000
  }
}
```

---

## 5. WebSockets (`/ws`)

### 5.1 Real-time Balance Updates
- **Endpoint**: `/ws/balance` (SockJS) ou `/ws/raw-balance` (raw)
- **Protocol**: STOMP over WebSocket
- **Authentication**: `?token=<jwt_token>` (query param) ou header STOMP `Authorization: Bearer <token>`
- **Subscription Topic**: `/topic/balance/{userId}`
- **Payload Pushed to Client**:
```json
{
  "walletId": 1,
  "walletName": "minha_carteira",
  "userId": 42,
  "newBalance": 1.55000000,
  "amount": 0.05000000,
  "context": "Pagamento do jantar"
}
```

### 5.2 Real-time Payment Request Notifications
- **Endpoint**: `/ws/payment-request` (SockJS) ou `/ws/raw-payment-request` (raw)
- **Protocol**: STOMP over WebSocket
- **Authentication**: `?token=<jwt_token>` (query param) ou header STOMP `Authorization: Bearer <token>`
- **Subscription Topic**: `/topic/payment-request/{linkId}`

O criador do link deve assinar este tópico imediatamente após criar o payment request. Quando o pagador executar `POST /ledger/payment-request/{linkId}/pay`, o backend publica o DTO completo com `status: PAID` neste tópico sem nenhum delay.

- **Payload Pushed to Client** (quando o link é pago):
```json
{
  "id": "link-uuid-1234",
  "requesterUserId": 1,
  "receiverWalletName": "minha_carteira",
  "amount": 0.0125,
  "status": "PAID",
  "expiresAt": "2023-10-01T18:00:00",
  "createdAt": "2023-10-01T17:00:00",
  "paidAt": "2023-10-01T17:30:00"
}
```

**Exemplo de integração (Flutter/Dart):**
```dart
stompClient.subscribe(
  destination: '/topic/payment-request/$linkId',
  callback: (frame) {
    final req = jsonDecode(frame.body!);
    if (req['status'] == 'PAID') {
      // Exibir confirmacao de pagamento
    }
  },
);
```

---

## 6. Merkle Audit (`/audit`)

O sistema gera automaticamente um checkpoint de auditoria a cada 5 minutos (configurável via `audit.merkle.interval-ms`). Cada checkpoint calcula a raiz de uma árvore de Merkle SHA-256 sobre **todos os saldos internos**, provando que nenhum saldo foi alterado silenciosamente — sem revelar os donos.

Todos os endpoints requerem autenticação JWT. O endpoint de trigger manual requer role `ADMIN`.

### 6.1 Get Latest Merkle Root
- **URL**: `/audit/latest-root`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "id": "uuid-do-checkpoint",
  "merkleRoot": "a3f5d2e1b4c8...",
  "ledgerCount": 42,
  "createdAt": "2026-02-25T16:00:00",
  "anchorTxid": ""
}
```

| Campo | Descrição |
|---|---|
| `merkleRoot` | Hash SHA-256 de 64 hex chars — raiz da árvore Merkle de todos os saldos |
| `ledgerCount` | Número de ledgers incluídos no snapshot |
| `anchorTxid` | (Futuro) txid do OP_RETURN Bitcoin que âncora este root na blockchain |

- **Response Body** quando ainda não há checkpoints:
```json
{
  "merkleRoot": "NO_CHECKPOINT_YET",
  "ledgerCount": 0,
  "createdAt": "2026-02-25T16:52:00",
  "anchorTxid": ""
}
```

### 6.2 Get Audit History
- **URL**: `/audit/history`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Query Params**: `limit` (opcional, default `10`, máximo `50`)
- **Response Body** (200 OK):
```json
[
  {
    "id": "uuid-checkpoint-1",
    "merkleRoot": "a3f5d2e1b4c8...",
    "ledgerCount": 42,
    "createdAt": "2026-02-25T16:05:00",
    "anchorTxid": ""
  },
  {
    "id": "uuid-checkpoint-2",
    "merkleRoot": "9b2c4d6e8f0a...",
    "ledgerCount": 41,
    "createdAt": "2026-02-25T16:00:00",
    "anchorTxid": ""
  }
]
```

### 6.3 Trigger Manual Audit
- **URL**: `/audit/trigger`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>` *(role `ADMIN` obrigatória)*
- **Response Body** (200 OK):
```json
{
  "id": "uuid-novo-checkpoint",
  "merkleRoot": "f8e7d6c5b4a3...",
  "ledgerCount": 42,
  "createdAt": "2026-02-25T16:52:56",
  "anchorTxid": ""
}
```
- **Error Response** (403 Forbidden — sem role ADMIN):
```json
{
  "success": false,
  "message": "Access Denied",
  "errorCode": "ERR_FORBIDDEN"
}
```

---

## 7. Voucher & Onboarding System (`/voucher`)

O sistema exige uma taxa de ativação "Onboarding" (100 BRL cotados dinamicamente em BTC). 
Após o cadastro e login, o usuário terá `isActive = false` e precisará gerar um *Onboarding Link* e realizar o pagamento on-chain para liberar a conta.

### 7.1 Generate Onboarding Payment Link
- **URL**: `/voucher/onboarding-link?sessionId={sessionId}`
- **Method**: `POST`
- **Description**: Gera um link de pagamento (usando o sistema `PaymentLinkService`) cobrando exatamente 100 BRL convertidos para BTC em tempo real pela API da Binance. É retornada uma entidade `PaymentLinkDTO` com o endereço para depósito e o status "pending".
- **Headers**: Nenhuma (requer `sessionId` obtido no `/auth/signup/totp/verify` via query param).
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Onboarding Payment Link generated successfully.",
  "data": {
    "id": "pay_3b2f91e98d8c",
    "userId": 12,
    "amountBtc": 0.00030250,
    "description": "ONBOARDING_VOUCHER",
    "depositAddress": "1A1z7agoat7F...",
    "status": "pending",
    "createdAt": "2026-02-28T14:32:00Z",
    "expiresAt": "2026-02-28T15:32:00Z",
    "paidAt": null,
    "completedAt": null,
    "txid": null
  }
}
```
> **Nota do Fluxo:** O usuário deve pagar o valor exato no endereço fornecido. O Kerosene através do `OnboardingMonitorService` fará polling aguardando o pagamento acumular **3 confirmações na blockchain**. Somente após as 3 confirmações, o usuário e a passkey serão efetivamente salvos no PostgreSQL (`isActive=true`) e uma notificação WebSocket será disparada confirmando a criação da conta.

### 7.2 Request Voucher (Legacy System Request)
- **URL**: `/voucher/request`
- **Method**: `POST`
- **Description**: Solicita os dados de pagamento em satoshis para um Voucher de pré-depósito. Requer autenticação de JWT válida. Retorna o endereço Bitcoin e a quantidade em sats para o envio on-chain.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Voucher requested. Please send the exact amount of satoshis...",
  "data": {
    "depositAddress": "bc1qxy2kgdygjrsqtzq2n0yrf249x3p...",
    "amountSats": 100000,
    "pendingVoucherId": "pend_vch_192e21b8..."
  }
}
```

### 7.3 Confirm Payment (Legacy System Request)
- **URL**: `/voucher/confirm?pendingVoucherId={..}&txid={..}`
- **Method**: `POST`
- **Description**: Confirma o TXID que o usuário fez em resposta a `/voucher/request`, validando-o na blockchain. Se for válido, emite o código (Voucher Code) para uso posterior.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Voucher paid and confirmed successfully.",
  "data": "VCH-A8B9C1-D2E3F4" // The Voucher Code string
}
```

### 7.4 Verify Voucher (Legacy Action)
- **URL**: `/voucher/verify?code={codigo_voucher}`
- **Method**: `GET`
- **Description**: Verifica se um voucher código existe e qual seu saldo. Pode ser usado publicamente antes do registro.
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Voucher is valid.",
  "data": {
    "code": "VCH-A8B9C1-D2E3F4",
    "valueBtc": 0.015,
    "status": "UNUSED",
    "expiresAt": "2026-03-01T23:59:59"
  }
}
```
