# Kerosene Backend Complete Documentation

## 1. System Architecture
Kerosene Backend is a modern, high-performance financial microservice built to manage internal ledgers, wallets, authentication, and external on-chain Bitcoin transactions.

### Key Technologies
- **Language**: Java 21+
- **Framework**: Spring Boot 3
- **Security**: Spring Security with stateless JWT Authentication
- **Database**: PostgreSQL (Relational Data) & Hibernate/JPA
- **Caching/State**: Redis (Rate limiting, session metadata)
- **Concurrency**: Java Virtual Threads (`spring.threads.virtual.enabled=true`)
- **Blockchain Integration**: Custom integration utilizing Bitcoin cryptography and external RPCs.

### Clean Architecture Principles
The project strictly separates its domain logic from infrastructure concerns.
- **Controllers**: Handle HTTP/WebSocket requests, extract metadata, and delegate to Orchestrators.
- **Orchestrators (Use Cases)**: Centralize business flows, calling services and repositories.
- **Services**: Specific domain operations (Cryptography, Token Generation, Ledger Math).
- **Entities/Repositories**: Database mapping and direct access.

---

## 2. Database Schema

### 2.1 Users (`tb_users`)
- `id` (Long, PK)
- `username` (String, Unique)
- `password` (String, hashed)
- `totp_secret` (String, encrypted Base32 for 2FA)

### 2.2 User Devices (`tb_user_devices`)
- `id` (Long, PK)
- `user_id` (Long, FK to Users)
- `device_hash` (String)

### 2.3 Wallets (`tb_wallets`)
- `id` (Long, PK)
- `name` (String, Unique per user)
- `passphrase_hash` (String, Hashed BIP-39 mnemonic)
- `user_id` (Long, FK)

### 2.4 Ledgers (`tb_ledgers`)
- `id` (Long, PK)
- `wallet_id` (Long, FK)
- `balance` (BigDecimal)

### 2.5 Transaction History (`tb_ledger_transactions`)
- `id` (UUID, PK)
- `ledger_id` (Long, FK)
- `amount` (BigDecimal)
- `type` (String: INTERNAL, EXTERNAL_DEPOSIT, EXTERNAL_WITHDRAWAL)
- `sender_identifier` / `receiver_identifier` (String)

---

## 3. Security Mechanism

### Authentication Flow
1. **Initial Login**: User/Pass -> TOTP Challenge.
2. **TOTP + Device Hash**: 2FA code + Device Hash -> JWT.
3. **JWT Authorization**: Requests validated via Header `Authorization`.

---

## 4. API Endpoints Documentation

This section provides the **exato JSON Request e Response body** for every endpoint. All responses are wrapped in our standard `ApiResponse` object.

### 4.1 Authentication & Users (`/auth`)

#### 4.1.1 Login Initiation
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
  "data": "temp-auth-id-12345"
}
```
- **Error Responses**: 
  - `ERR_AUTH_USER_NOT_FOUND` (404)
  - `ERR_AUTH_INVALID_CREDENTIALS` (401)

#### 4.1.2 User Registration (Signup)
- **URL**: `/auth/signup`
- **Method**: `POST`
- **Request Body**:
```json
{
  "username": "newuser",
  "passphrase": "mypassword"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "User registered. Please configure your authenticator app using the provided setup key.",
  "data": "JBSWY3DPEHPK3PXP" // Exemplo de TOTP Secret Base32
}
```
- **Error Responses**: 
  - `ERR_AUTH_USER_ALREADY_EXISTS` (409)

#### 4.1.3 Signup TOTP Verification
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
  "message": "Autenticação concluída.",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // JWT Token
}
```
- **Error Response Exemplo**:
```json
{
  "success": false,
  "message": "The provided TOTP code is incorrect or expired.",
  "errorCode": "ERR_AUTH_INCORRECT_TOTP"
}
```

#### 4.1.4 Login TOTP Verification
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
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // JWT Token
}
```

### 4.2 Wallet Management (`/wallet`)

#### 4.2.1 Create Wallet
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

#### 4.2.2 Get All Wallets
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

#### 4.2.3 Find Wallet By Name
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

#### 4.2.4 Update Wallet
- **URL**: `/wallet/update`
- **Method**: `PUT`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "minhacarteira",
  "newName": "carteira_nova",
  "passphrase": "senha_que_valida"
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

#### 4.2.5 Delete Wallet
- **URL**: `/wallet/delete`
- **Method**: `DELETE`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "carteira_nova",
  "passphrase": "senha_que_valida"
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

### 4.3 Ledger & Internal Financials (`/ledger`)

#### 4.3.1 Process Internal Transaction
- **URL**: `/ledger/transaction`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "sender": "minha_carteira", 
  "receiver": "amigo_username",
  "amount": 0.05,
  "context": "Pagamento da pizza"
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
    "amount": 0.05,
    "context": "Pagamento da pizza"
  }
}
```
- **Error Response Exemplo**:
```json
{
  "success": false,
  "message": "Transaction Failed: Your ledger has insufficient balance.",
  "errorCode": "ERR_LEDGER_INSUFFICIENT_BALANCE"
}
```

#### 4.3.2 Get Transaction History
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
      "id": "e6a133f6-4ee7-4286-bc9b-13fa69766bb4",
      "senderIdentifier": "minha_carteira",
      "senderUserId": 1,
      "receiverIdentifier": "amigo_username",
      "receiverUserId": 2,
      "transactionType": "INTERNAL",
      "amount": 0.05000000,
      "status": "CONCLUDED",
      "networkFee": null,
      "blockchainTxid": null,
      "context": "Pagamento da pizza",
      "createdAt": "2023-10-01T15:00:00"
    }
  ]
}
```

#### 4.3.3 Get All Ledgers
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

#### 4.3.5 Get Balance
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

#### 4.3.6 Create Internal Payment Request
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
    "id": "link-uuid-exemplo-8a2b",
    "requesterUserId": 1,
    "receiverWalletName": "minha_carteira",
    "amount": 0.0125,
    "status": "PENDING",
    "expiresAt": "2023-10-02T18:00:00",
    "createdAt": "2023-10-01T18:00:00",
    "paidAt": null
  }
}
```

#### 4.3.7 Retrieve Payment Request
- **URL**: `/ledger/payment-request/{linkId}`
- **Method**: `GET`
- **Headers**: `Authorization: Bearer <token>`
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "link-uuid-exemplo-8a2b",
    "requesterUserId": 1,
    "receiverWalletName": "minha_carteira",
    "amount": 0.0125,
    "status": "PENDING",
    "expiresAt": "2023-10-02T18:00:00",
    "createdAt": "2023-10-01T18:00:00",
    "paidAt": null
  }
}
```

#### 4.3.8 Pay Payment Request
- **URL**: `/ledger/payment-request/{linkId}/pay`
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
    "id": "link-uuid-exemplo-8a2b",
    "requesterUserId": 1,
    "receiverWalletName": "minha_carteira",
    "amount": 0.0125,
    "status": "PAID",
    "expiresAt": "2023-10-02T18:00:00",
    "createdAt": "2023-10-01T18:00:00",
    "paidAt": "2023-10-01T18:05:00"
  }
}
```

### 4.4 On-Chain Bitcoin Transactions (`/transactions`)

#### 4.4.1 Get Global Deposit Address
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

#### 4.4.2 Estimate Network Fee
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
    "amountReceived": 1.50000000,
    "totalToSend": 1.50004250
  }
}
```

#### 4.4.3 Create Unsigned Transaction
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

#### 4.4.4 Broadcast Signed Transaction
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

#### 4.4.5 On-Chain Withdrawal
- **URL**: `/transactions/withdraw`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "fromWalletName": "minhacarteira",
  "toAddress": "bc1_destino...",
  "amount": 0.1,
  "description": "Retirada"
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

### 4.5 Mobile Push Notifications (`/notifications`)

#### 4.5.1 Register Device Token
- **URL**: `/notifications/register-token`
- **Method**: `POST`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "token": "firebase_fcm_token_123"
}
```
- **Response Body** (200 OK):
```json
{
  "success": true,
  "message": "Token registered",
  "data": "SUCCESS"
}
```

### 4.6 WebSockets (`/ws`)

#### 4.6.1 Real-time Balance Updates
- **Endpoint**: `/ws/balance/websocket`
- **Protocol**: `ws://` or `wss://` (STOMP over WebSocket)
- **Subscription Topic**: `/user/queue/balance`
- **Authentication**: `?token=<jwt_token>`
- **WebSocket Frame Pushed Data**:
*(Atenção: O Frame WebSocket envia o JSON puro da mensagem diretamente ao inscrito)*
```json
{
  "type": "BALANCE_UPDATE",
  "newBalance": 1.55000000,
  "transactionId": "uuid-da-transacao",
  "message": "Você recebeu 0.50 BTC"
}
```
