# Transactions, Network e Economy API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `28`.

Controllers cobertos:

- `BlockchainVisualizationController`
- `DepositController`
- `EconomyController`
- `NetworkPaymentsController`
- `OnrampController`
- `TransactionController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/api/economy/btc-price` | `EconomyController.getBtcPrice` | AUTHENTICATED | none | `ApiResponse<Map<String, Object>>` | [EconomyController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/EconomyController.java#L49) |
| `GET` | `/api/economy/status` | `EconomyController.getEconomyStatus` | AUTHENTICATED | none | `ApiResponse<Map<String, Object>>` | [EconomyController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/EconomyController.java#L35) |
| `GET` | `/api/onramp/urls` | `OnrampController.getOnrampUrls` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: walletName: String, amountBtc: BigDecimal | `ApiResponse<Map<String, String>>` | [OnrampController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/OnrampController.java#L39) |
| `POST` | `/deposit/{transferId}/cancel` | `DepositController.cancelDeposit` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: transferId: UUID | `ApiResponse<ExternalTransferResponseDTO>` | [DepositController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/DepositController.java#L28) |
| `POST` | `/transactions/broadcast` | `TransactionController.broadcastTransaction` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: BroadcastTransactionDTO | `ApiResponse<TransactionResponseDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L152) |
| `POST` | `/transactions/create-payment-link` | `TransactionController.createPaymentLink` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: CreatePaymentLinkRequest | `ApiResponse<PaymentLinkDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L174) |
| `POST` | `/transactions/create-unsigned` | `TransactionController.createUnsignedTransaction` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: TransactionRequestDTO | `ApiResponse<UnsignedTransactionDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L119) |
| `GET` | `/transactions/deposit-address` | `TransactionController.getDepositAddress` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: expectedAmountBtc: BigDecimal | `ApiResponse<String>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L70) |
| `GET` | `/transactions/estimate-fee` | `TransactionController.estimateFee` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: amount: BigDecimal | `ApiResponse<EstimatedFeeDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L102) |
| `POST` | `/transactions/network/lightning/invoice` | `NetworkPaymentsController.createLightningInvoice` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: LightningInvoiceRequestDTO | `ApiResponse<LightningInvoiceResponseDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L74) |
| `POST` | `/transactions/network/lightning/pay` | `NetworkPaymentsController.payLightning` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: LightningPaymentRequestDTO | `ApiResponse<ExternalTransferResponseDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L95) |
| `POST` | `/transactions/network/onchain/address` | `NetworkPaymentsController.issueOnchainAddress` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: OnchainAddressRequestDTO | `ApiResponse<OnchainAddressAllocationDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L41) |
| `POST` | `/transactions/network/onchain/send` | `NetworkPaymentsController.sendOnchain` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: OnchainSendRequestDTO | `ApiResponse<ExternalTransferResponseDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L62) |
| `GET` | `/transactions/network/transfers` | `NetworkPaymentsController.listTransfers` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ApiResponse<List<ExternalTransferResponseDTO>>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L107) |
| `GET` | `/transactions/network/transfers/{transferId}` | `NetworkPaymentsController.getTransfer` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: transferId: UUID | `ApiResponse<ExternalTransferResponseDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L114) |
| `POST` | `/transactions/network/transfers/{transferId}/cancel` | `NetworkPaymentsController.cancelInboundTransfer` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: transferId: UUID | `ApiResponse<ExternalTransferResponseDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L85) |
| `GET` | `/transactions/network/wallet-profile` | `NetworkPaymentsController.getWalletNetworkProfile` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: walletName: String | `ApiResponse<WalletNetworkAddressDTO>` | [NetworkPaymentsController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java#L52) |
| `GET` | `/transactions/payment-link/{linkId}` | `TransactionController.getPaymentLink` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: linkId: String | `ApiResponse<PaymentLinkDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L191) |
| `POST` | `/transactions/payment-link/{linkId}/cancel` | `TransactionController.cancelPayment` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: linkId: String<br>body: CancelPaymentLinkRequest (optional) | `ApiResponse<PaymentLinkDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L251) |
| `POST` | `/transactions/payment-link/{linkId}/complete` | `TransactionController.completePayment` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: linkId: String | `ApiResponse<PaymentLinkDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L233) |
| `POST` | `/transactions/payment-link/{linkId}/confirm` | `TransactionController.confirmPayment` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: linkId: String<br>body: ConfirmPaymentRequest | `ApiResponse<PaymentLinkDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L211) |
| `GET` | `/transactions/payment-links` | `TransactionController.getUserPaymentLinks` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ApiResponse<List<PaymentLinkDTO>>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L278) |
| `GET` | `/transactions/status` | `TransactionController.getStatus` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: txid: String | `ApiResponse<TransactionResponseDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L136) |
| `GET` | `/transactions/visualization` | `BlockchainVisualizationController.dashboard` | AUTHENTICATED | none | `ApiResponse<BlockchainMonitorService.BlockchainVisualizationSnapshot>` | [BlockchainVisualizationController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/BlockchainVisualizationController.java#L26) |
| `GET` | `/transactions/visualization/blockchain` | `BlockchainVisualizationController.blockchain` | AUTHENTICATED | none | `ApiResponse<BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot>` | [BlockchainVisualizationController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/BlockchainVisualizationController.java#L33) |
| `POST` | `/transactions/visualization/blockchain/sync` | `BlockchainVisualizationController.triggerBlockchainSync` | AUTHENTICATED | none | `ApiResponse<Map<String, Object>>` | [BlockchainVisualizationController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/BlockchainVisualizationController.java#L47) |
| `GET` | `/transactions/visualization/lightning` | `BlockchainVisualizationController.lightning` | AUTHENTICATED | none | `ApiResponse<LightningNetworkMonitorService.LightningMonitorSnapshot>` | [BlockchainVisualizationController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/BlockchainVisualizationController.java#L40) |
| `POST` | `/transactions/withdraw` | `TransactionController.withdraw` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: WithdrawRequestDTO | `ApiResponse<TransactionResponseDTO>` | [TransactionController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java#L294) |

## DTOs e Payloads

### `BroadcastTransactionDTO`

Fonte: [BroadcastTransactionDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/BroadcastTransactionDTO.java)

Campos observados no DTO:

- `rawTxHex: String`
- `toAddress: String`
- `amount: java.math.BigDecimal`
- `message: String`

### `CancelPaymentLinkRequest`

Fonte: [CancelPaymentLinkRequest.java](../../../backend/kerosene/src/main/java/source/transactions/dto/CancelPaymentLinkRequest.java)

Campos observados no DTO:

- `reason: String`

### `ConfirmPaymentRequest`

Fonte: [ConfirmPaymentRequest.java](../../../backend/kerosene/src/main/java/source/transactions/dto/ConfirmPaymentRequest.java)

Campos observados no DTO:

- `idempotencyKey: String`
- `txid: String`
- `fromAddress: String`

### `CreatePaymentLinkRequest`

Fonte: [CreatePaymentLinkRequest.java](../../../backend/kerosene/src/main/java/source/transactions/dto/CreatePaymentLinkRequest.java)

Campos observados no DTO:

- `amount: BigDecimal`
- `description: String`
- `expiresInMinutes: Integer`
- `visibility: String`
- `confirmationMode: String`
- `amountLocked: Boolean`
- `referenceLabel: String`
- `metadata: Map<String, String>`

### `EstimatedFeeDTO`

Fonte: [EstimatedFeeDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/EstimatedFeeDTO.java)

Campos observados no DTO:

- `fastSatoshisPerByte: Long`
- `standardSatoshisPerByte: Long`
- `slowSatoshisPerByte: Long`
- `estimatedFastBtc: BigDecimal`
- `estimatedStandardBtc: BigDecimal`
- `estimatedSlowBtc: BigDecimal`
- `amountReceived: BigDecimal`
- `totalToSend: BigDecimal`

### `ExternalTransferResponseDTO`

Fonte: [ExternalTransferResponseDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/ExternalTransferResponseDTO.java)

Campos observados no DTO:

- `UUID id`
- `String network`
- `String transferType`
- `String status`
- `String provider`
- `String walletName`
- `String destination`
- `String invoiceId`
- `String blockchainTxid`
- `String paymentHash`
- `String invoiceData`
- `BigDecimal expectedAmountBtc`
- `BigDecimal amountBtc`
- `BigDecimal networkFeeBtc`
- `BigDecimal platformFeeBtc`
- `BigDecimal totalDebitedBtc`
- `String externalReference`
- `Integer confirmations`
- `LocalDateTime expiresAt`
- `LocalDateTime detectedAt`
- `LocalDateTime settledAt`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`
- `String context`

### `LightningInvoiceRequestDTO`

Fonte: [LightningInvoiceRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/LightningInvoiceRequestDTO.java)

Campos observados no DTO:

- `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters") String idempotencyKey`
- `@NotBlank(message = "walletName is required") String walletName`
- `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places") BigDecimal amount`
- `String memo`
- `Integer expiresInSeconds`

### `LightningInvoiceResponseDTO`

Fonte: [LightningInvoiceResponseDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/LightningInvoiceResponseDTO.java)

Campos observados no DTO:

- `UUID transferId`
- `String walletName`
- `String paymentRequest`
- `String paymentHash`
- `String lightningAddress`
- `BigDecimal amountBtc`
- `String provider`
- `LocalDateTime expiresAt`
- `String status`

### `LightningPaymentRequestDTO`

Fonte: [LightningPaymentRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/LightningPaymentRequestDTO.java)

Campos observados no DTO:

- `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters") String idempotencyKey`
- `@NotBlank(message = "fromWalletName is required") String fromWalletName`
- `@NotBlank(message = "paymentRequest is required") String paymentRequest`
- `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places") BigDecimal amount`
- `BigDecimal maxRoutingFeeBtc`
- `String description`
- `String totpCode`
- `String passkeyAssertionResponseJSON`
- `String confirmationPassphrase`

### `OnchainAddressAllocationDTO`

Fonte: [OnchainAddressAllocationDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/OnchainAddressAllocationDTO.java)

Campos observados no DTO:

- `String walletName`
- `String onchainAddress`
- `BigDecimal expectedAmountBtc`
- `String network`
- `String provider`
- `String externalWalletReference`
- `String walletMode`
- `UUID transferId`
- `String transferStatus`
- `Integer confirmations`
- `Integer requiredConfirmations`
- `String blockchainTxid`

### `OnchainAddressRequestDTO`

Fonte: [OnchainAddressRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/OnchainAddressRequestDTO.java)

Campos observados no DTO:

- `String walletName`
- `BigDecimal expectedAmountBtc`

### `OnchainSendRequestDTO`

Fonte: [OnchainSendRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/OnchainSendRequestDTO.java)

Campos observados no DTO:

- `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters") String idempotencyKey`
- `@NotBlank(message = "fromWalletName is required") String fromWalletName`
- `@NotBlank(message = "toAddress is required") String toAddress`
- `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places") BigDecimal amount`
- `String description`
- `String totpCode`
- `String passkeyAssertionResponseJSON`
- `String confirmationPassphrase`

### `PaymentLinkDTO`

Fonte: [PaymentLinkDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/PaymentLinkDTO.java)

Campos observados no DTO:

- `id: String`
- `userId: Long`
- `sessionId: String`
- `amountBtc: BigDecimal`
- `grossAmountBtc: BigDecimal`
- `depositFeeBtc: BigDecimal`
- `netAmountBtc: BigDecimal`
- `description: String`
- `depositAddress: String`
- `visibility: String`
- `confirmationMode: String`
- `amountLocked: Boolean`
- `referenceLabel: String`
- `metadata: Map<String, String>`
- `status: String`
- `txid: String`
- `expiresAt: LocalDateTime`
- `createdAt: LocalDateTime`
- `paidAt: LocalDateTime`
- `completedAt: LocalDateTime`
- `cancelledAt: LocalDateTime`
- `cancelReason: String`
- `paymentRail: String`
- `paymentIntentStatus: String`

### `TransactionRequestDTO`

Fonte: [TransactionRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/TransactionRequestDTO.java)

Campos observados no DTO:

- `fromAddress: String`
- `toAddress: String`
- `amount: BigDecimal`
- `feeSatoshis: Long`

### `TransactionResponseDTO`

Fonte: [TransactionResponseDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/TransactionResponseDTO.java)

Campos observados no DTO:

- `txid: String`
- `status: String`
- `feeSatoshis: Long`
- `amountReceived: BigDecimal`
- `sender: String`
- `receiver: String`
- `context: String`

### `UnsignedTransactionDTO`

Fonte: [UnsignedTransactionDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/UnsignedTransactionDTO.java)

Campos observados no DTO:

- `rawTxHex: String`
- `txId: String`
- `inputs: List<TransactionInput>`
- `outputs: List<TransactionOutput>`
- `totalAmount: BigDecimal`
- `fee: Long`
- `fromAddress: String`
- `toAddress: String`
- `txid: String`
- `vout: Integer`
- `value: BigDecimal`
- `scriptPubKey: String`
- `address: String`
- `value: BigDecimal`

### `WalletNetworkAddressDTO`

Fonte: [WalletNetworkAddressDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/WalletNetworkAddressDTO.java)

Campos observados no DTO:

- `String walletName`
- `String onchainAddress`
- `String lightningAddress`
- `String network`
- `String provider`
- `String externalWalletReference`
- `String walletMode`
- `boolean lightningEnabled`
- `String lightningUnavailableReason`

### `WithdrawRequestDTO`

Fonte: [WithdrawRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/WithdrawRequestDTO.java)

Campos observados no DTO:

- `idempotencyKey: String`
- `fromWalletName: String`
- `toAddress: String`
- `amount: BigDecimal`
- `description: String`
- `totpCode: String`
- `passkeyAssertionResponseJSON: String`
- `passkeyAssertionRequestJSON: String`
- `confirmationPassphrase: String`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
