# 📘 Kerosene Backend API Documentation
> **Version:** 1.3
> **Last Updated:** 2026-02-17

This document is the **definitive reference** for the Kerosene Backend API. It includes every controller, endpoint, request body, and response structure.

---

## 🛠️ Global Configuration

### Base URLs
- **Localhost (Simulator/Web):** `http://localhost:8080`
- **Android Emulator:** `http://10.0.2.2:8080` (Requires `adb reverse tcp:8080 tcp:8080` recommended)
- **Ngrok (Remote):** `https://disingenuously-undelightful-lino.ngrok-free.dev`

### Common Headers
| Header | Required | Description |
| :--- | :--- | :--- |
| `Content-Type` | **Yes** | `application/json` (unless specified otherwise) |
| `Authorization` | *Yes (Auth routes)* | `Bearer <JWT_TOKEN>` |
| `X-Device-Hash` | **Yes (ALL)** | Unique device identifier hash. Required for 2FA/Security. |

### Token Refresh
- **Header:** `X-New-Token`
- **Behavior:** If this header is present in *any* response, the client **MUST** replace its locally stored JWT with the new value immediately.

---

## 1. Auth Controller (`/auth`)
*Handles user registration, login, and 2FA.*

### `POST /auth/login`
Authenticates a user.
- **Request Body (`UserDTO`):**
  ```json
  {
    "username": "user123",
    "passphrase": "abandon ability able about above absent absorb abstract absurd abuse access accident"
  }
  ```
- **Response (202 Accepted):** `text/plain`
  - **Format:** `<USER_ID> <JWT_TOKEN>`
  - **Example:** `1 eyJhbGciOiJIUzI1NiJ9...`

### `POST /auth/login/totp/verify`
Completes login if the device hash is not recognized.
- **Request Body (`UserDTO`):**
  ```json
  {
    "username": "user123",
    "passphrase": "...",
    "totpCode": "123456"
  }
  ```
- **Response (202 Accepted):** `text/plain` (Same as login)

### `POST /auth/signup`
Initiates user registration.
- **Request Body (`UserDTO`):** (Same as Login)
- **Response (202 Accepted):** `text/plain`
  - **Content:** The **TOTP Secret Key** (Base32 string) to be imported into Google Authenticator.

### `POST /auth/signup/totp/verify`
Finalizes registration by verifying the TOTP code.
- **Request Body (`UserDTO`):** (Same as Login 2FA)
- **Response (202 Accepted):** `text/plain` (Returns session: `ID TOKEN`)

---

## 2. Wallet Controller (`/wallet`)
*Manages user wallets (creation, listing, updating).*

### `POST /wallet/create`
Creates a new wallet for the authenticated user.
- **Request Body (`WalletDTO`):**
  ```json
  {
    "name": "MAIN",
    "passphrase": "..." 
  }
  ```
- **Response (201 Created):** `text/plain` ("wallet created")

### `GET /wallet/all`
Lists all wallets for the user.
- **Response (200 OK):** JSON Array
  ```json
  [
    {
      "id": 1,
      "identificator": "MAIN", // Wallet Name or ID
      "address": "1A1z..."
    }
  ]
  ```

### `GET /wallet/find`
Finds a specific wallet.
- **Parameters:** `?name=<WALLET_NAME>`
- **Response (200 OK):** JSON Object (Wallet details)

### `PUT /wallet/update`
Renames a wallet.
- **Request Body (`WalletDTO`):**
  ```json
  {
    "name": "OLD_NAME",
    "newName": "NEW_NAME"
  }
  ```
- **Response (200 OK):** `text/plain` ("wallet updated")

### `DELETE /wallet/delete`
Deletes a wallet.
- **Request Body (`WalletDTO`):** `{ "name": "MAIN" }`
- **Response (200 OK):** `text/plain` ("wallet deleted")

---

## 🔌 WebSocket - Real-Time Balance Updates

### Connection Endpoint
- **URL:** `ws://{HOST}/ws/balance` (or `wss://` for HTTPS)
- **Protocol:** STOMP over WebSocket with SockJS fallback

### Subscription
Subscribe to balance updates for the authenticated user:
```
DESTINATION: /topic/balance/{userId}
```

### Message Format
When a balance changes, all subscribed clients receive:
```json
{
  "walletId": 1,
  "walletName": "MAIN",
  "userId": 5,
  "newBalance": 125.50000000,
  "amount": 10.00000000,
  "context": "transfer",
  "timestamp": "2026-02-17T20:25:00"
}
```

### Flutter/Dart Integration Example
```dart
import 'package:stomp_dart_client/stomp.dart';

final stompClient = StompClient(
  config: StompConfig.sockJS(
    url: 'https://YOUR_HOST/ws/balance',
    onConnect: (frame) {
      stompClient.subscribe(
        destination: '/topic/balance/$userId',
        callback: (frame) {
          final update = jsonDecode(frame.body!);
          // Update UI with update['newBalance']
        },
      );
    },
  ),
);

stompClient.activate();
```

---

## 3. Ledger Controller (`/ledger`)
*Handles internal, off-chain transactions between users/wallets.*

### `GET /ledger/all`
Lists all ledger entries for the user.
- **Response (200 OK):** `List<LedgerDTO>`
  ```json
  [
    {
      "id": 101,
      "walletId": 1,
      "walletName": "MAIN",
      "balance": 50.0,
      "amount": 10.0,
      "context": "transfer",
      "lastHash": "..."
    }
  ]
  ```

### `GET /ledger/balance`
Gets the numerical balance of a specific wallet.
- **Parameters:** `?walletName=<NAME>`
- **Response (200 OK):** `BigDecimal` (e.g., `12.50000000`)

### `POST /ledger/transaction`
Executes an internal transfer.
- **Request Body (`TransactionDTO`):**
  ```json
  {
    "sender": "MY_WALLET_NAME",    // Source Wallet Name or ID
    "receiver": "DEST_USERNAME",   // Destination Username OR Wallet Address
    "amount": 0.5,
    "context": "transfer"
  }
  ```
  > **IMP:** Use `sender`/`receiver` keys. Do NOT use `fromWalletId`/`toWalletId`.

---

## 4. Transaction Controller (`/transactions`)
*Handles On-Chain Bitcoin transactions, Deposits, and Payment Links.*

### 4.1. Bitcoin Operations

#### `GET /transactions/estimate-fee`
- **Parameters:** `?amount=<BTC_VALUE>`
- **Response (`EstimatedFeeDTO`):**
  ```json
  {
    "fastSatPerByte": 50,
    "standardSatPerByte": 35,
    "slowSatPerByte": 15,
    "estimatedFastBtc": 0.00005,
    "estimatedStandardBtc": 0.000035,
    "estimatedSlowBtc": 0.000015
  }
  ```

#### `POST /transactions/create-unsigned`
Creates a raw transaction for the client to sign.
- **Request Body (`TransactionRequestDTO`):**
  ```json
  {
    "fromAddress": "1SenderAddress...",
    "toAddress": "1ReceiverAddress...",
    "amount": 0.1,
    "feeSatoshis": 3500
  }
  ```
- **Response (`UnsignedTransactionDTO`):**
  ```json
  {
    "txId": "temp-uuid...",
    "rawTxHex": "020000...", // The hex string to be signed by the wallet
    "fromAddress": "...",
    "toAddress": "...",
    "totalAmount": 0.1,
    "fee": 3500
  }
  ```

#### `POST /transactions/broadcast`
Broadcasts a signed transaction to the network.
- **Request Body (`BroadcastTransactionDTO`):**
  ```json
  {
    "rawTxHex": "020000...<SIGNED_BYTES>..."
  }
  ```
- **Response (`TransactionResponseDTO`):**
  ```json
  {
    "txid": "fed5...",
    "status": "pending",
    "feeSatoshis": 0
  }
  ```

#### `GET /transactions/status`
- **Parameters:** `?txid=<TXID>`
- **Response (`TransactionResponseDTO`):**
  ```json
  {
    "txid": "...",
    "status": "confirmed", // or "unconfirmed", "pending"
    "feeSatoshis": 5000
  }
  ```

---

### 4.2. Deposits

#### `GET /transactions/deposit-address`
- **Response:** `text/plain` (The server's central deposit address, e.g., `"1Server..."`)

#### `POST /transactions/confirm-deposit`
Notifies server of a deposit made to the central address.
- **Request Body (`DepositConfirmRequest`):**
  ```json
  {
    "txid": "...",
    "fromAddress": "...",
    "amount": 0.5
  }
  ```
- **Response (`DepositDTO`):**
  ```json
  {
    "id": 1,
    "status": "pending", // becomes "credited" upon blockchain confirmation
    "amountBtc": 0.5,
    "txid": "..."
  }
  ```

#### `GET /transactions/deposits`
Lists all user deposits.
- **Response:** `List<DepositDTO>`

#### `GET /transactions/deposit-balance`
- **Response:** `BigDecimal` (Sum of all credited deposits).

---

### 4.3. Payment Links

#### `POST /transactions/create-payment-link`
- **Request Body (`CreatePaymentLinkRequest`):**
  ```json
  {
    "amount": 0.05,
    "description": "Consultation Fee"
  }
  ```
- **Response (`PaymentLinkDTO`):**
  ```json
  {
    "id": "uuid-link-id",
    "amountBtc": 0.05,
    "description": "Consultation Fee",
    "depositAddress": "1Server...",
    "status": "pending",
    "expiresAt": "2026-02-17T..."
  }
  ```

#### `GET /transactions/payment-link/{linkId}`
Public endpoint to check link status.
- **Response:** `PaymentLinkDTO`

#### `POST /transactions/payment-link/{linkId}/confirm`
Confirm payment for a link.
- **Request Body (`ConfirmPaymentRequest`):**
  ```json
  {
    "txid": "...",
    "fromAddress": "..."
  }
  ```
- **Response:** `PaymentLinkDTO` (Status updates to `paid` if valid)

#### `GET /transactions/payment-links`
List all payment links created by the user.
- **Response:** `List<PaymentLinkDTO>`
