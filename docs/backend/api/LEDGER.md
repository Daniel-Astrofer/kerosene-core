# Ledger API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `8`.

Controllers cobertos:

- `LedgerController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/ledger/all` | `LedgerController.getAllLedgers` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ApiResponse<List<LedgerDTO>>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L119) |
| `GET` | `/ledger/balance` | `LedgerController.getBalance` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: walletName: String | `ApiResponse<BigDecimal>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L143) |
| `GET` | `/ledger/find` | `LedgerController.getLedgerByWalletName` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: walletName: String | `ApiResponse<LedgerDTO>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L128) |
| `GET` | `/ledger/history` | `LedgerController.getHistory` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: page: int, size: int | `ApiResponse<List<LedgerSyncEventDTO>>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L103) |
| `POST` | `/ledger/payment-request` | `LedgerController.createPaymentRequest` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: CreatePaymentRequestReq | `ApiResponse<InternalPaymentRequestDTO>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L253) |
| `GET` | `/ledger/payment-request/{linkId}` | `LedgerController.getPaymentRequest` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: linkId: String | `ApiResponse<PaymentRequestPublicDTO>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L262) |
| `POST` | `/ledger/payment-request/{linkId}/pay` | `LedgerController.payPaymentRequest` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: linkId: String<br>body: PayPaymentRequestReq | `ApiResponse<InternalPaymentRequestDTO>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L273) |
| `POST` | `/ledger/transaction` | `LedgerController.transaction` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: TransactionDTO | `ApiResponse<InternalTransactionResponseDTO>` | [LedgerController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java#L75) |

## DTOs e Payloads

### `InternalPaymentRequestDTO`

Fonte: [InternalPaymentRequestDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/InternalPaymentRequestDTO.java)

Campos observados no DTO:

- `id: String`
- `requesterUserId: Long`
- `receiverWalletId: Long`
- `receiverWalletName: String`
- `destinationHash: String`
- `amount: BigDecimal`
- `status: String`
- `expiresAt: LocalDateTime`
- `createdAt: LocalDateTime`
- `paidAt: LocalDateTime`

### `InternalTransactionResponseDTO`

Fonte: [InternalTransactionResponseDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/InternalTransactionResponseDTO.java)

Campos observados no DTO:

- `String txid`
- `String status`
- `BigDecimal amount`
- `String sender`
- `String receiver`
- `String context`

### `LedgerDTO`

Fonte: [LedgerDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/LedgerDTO.java)

Campos observados no DTO:

- `id: Integer`
- `walletId: Long`
- `walletName: String`
- `balance: BigDecimal`
- `nonce: Integer`
- `lastHash: String`
- `context: String`
- `amount: BigDecimal`

### `PaymentRequestPublicDTO`

Fonte: [PaymentRequestPublicDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/PaymentRequestPublicDTO.java)

Campos observados no DTO:

- `id: String`
- `amount: BigDecimal`
- `status: String`
- `expiresAt: LocalDateTime`
- `destinationHash: String`
- `locked: boolean`

### `TransactionDTO`

Fonte: [TransactionDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/TransactionDTO.java)

Campos observados no DTO:

- `sender: String`
- `receiver: String`
- `amount: BigDecimal`
- `context: String`
- `idempotencyKey: String`
- `requestTimestamp: Long`
- `passkeyAssertionJson: String`
- `confirmationPassphrase: String`
- `totpCode: String`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
