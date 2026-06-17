# KFE API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `14`.

Controllers cobertos:

- `KfeAuditAdminController`
- `KfeDashboardController`
- `KfeReceivingController`
- `KfeTransactionController`
- `KfeWalletController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/api/admin/kfe/audit/events` | `KfeAuditAdminController.events` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | query: limit: int | `ApiResponse<List<KfeAuditEventResponse>>` | [KfeAuditAdminController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java#L37) |
| `GET` | `/api/admin/kfe/audit/latest` | `KfeAuditAdminController.latest` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ApiResponse<KfeAuditLatestResponse>` | [KfeAuditAdminController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java#L32) |
| `POST` | `/api/admin/kfe/audit/root` | `KfeAuditAdminController.root` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ApiResponse<KfeAuditRootResponse>` | [KfeAuditAdminController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java#L51) |
| `GET` | `/api/admin/kfe/audit/transactions/{transactionId}` | `KfeAuditAdminController.transactionEvents` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | path: transactionId: UUID | `ApiResponse<List<KfeAuditEventResponse>>` | [KfeAuditAdminController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java#L43) |
| `GET` | `/kfe/dashboard` | `KfeDashboardController.dashboard` | AUTHENTICATED | none | `ApiResponse<KfeDashboardResponse>` | [KfeDashboardController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeDashboardController.java#L23) |
| `POST` | `/kfe/transactions` | `KfeTransactionController.submit` | AUTHENTICATED | body: KfeSubmitTransactionRequest | `ApiResponse<KfeTransactionResponse>` | [KfeTransactionController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java#L30) |
| `GET` | `/kfe/transactions/{transactionId}` | `KfeTransactionController.get` | AUTHENTICATED | path: transactionId: UUID | `ApiResponse<KfeTransactionResponse>` | [KfeTransactionController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java#L45) |
| `GET` | `/kfe/users/{receiverIdentifier}/receiving-capabilities` | `KfeReceivingController.capabilities` | AUTHENTICATED | path: receiverIdentifier: String | `ApiResponse<KfeReceivingCapabilitiesResponse>` | [KfeReceivingController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeReceivingController.java#L23) |
| `GET` | `/kfe/wallets` | `KfeWalletController.list` | AUTHENTICATED | none | `ApiResponse<List<KfeWalletResponse>>` | [KfeWalletController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java#L46) |
| `POST` | `/kfe/wallets` | `KfeWalletController.create` | AUTHENTICATED | body: KfeCreateWalletRequest | `ApiResponse<KfeWalletResponse>` | [KfeWalletController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java#L37) |
| `GET` | `/kfe/wallets/names` | `KfeWalletController.names` | AUTHENTICATED | none | `ApiResponse<List<KfeWalletNameOption>>` | [KfeWalletController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java#L53) |
| `POST` | `/kfe/wallets/{walletId}/addresses/rotate` | `KfeWalletController.rotateAddress` | AUTHENTICATED | path: walletId: UUID | `ApiResponse<KfeAddressResponse>` | [KfeWalletController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java#L60) |
| `POST` | `/kfe/wallets/{walletId}/cold-wallet/psbt` | `KfeWalletController.createColdWalletPsbt` | AUTHENTICATED | path: walletId: UUID<br>body: KfeColdWalletPsbtRequest | `ApiResponse<KfeColdWalletPsbtResponse>` | [KfeWalletController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java#L78) |
| `GET` | `/kfe/wallets/{walletId}/utxos` | `KfeWalletController.listUtxos` | AUTHENTICATED | path: walletId: UUID | `ApiResponse<List<KfeUtxoResponse>>` | [KfeWalletController.java](../../../backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java#L69) |

## DTOs e Payloads

### `KfeAddressResponse`

Fonte: [KfeAddressResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAddressResponse.java)

Campos observados no DTO:

- `UUID id`
- `UUID walletId`
- `String address`
- `KfeWalletAddressRole role`
- `KfeWalletAddressStatus status`
- `String derivationPath`
- `Integer derivationIndex`
- `String providerReference`
- `LocalDateTime createdAt`
- `LocalDateTime retiredAt`

### `KfeAuditEventResponse`

Fonte: [KfeAuditEventResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAuditEventResponse.java)

Campos observados no DTO:

- `Long sequenceNumber`
- `UUID id`
- `UUID transactionId`
- `UUID walletId`
- `String eventType`
- `String fromStatus`
- `String toStatus`
- `String payloadHash`
- `String previousHash`
- `String eventHash`
- `LocalDateTime createdAt`

### `KfeAuditLatestResponse`

Fonte: [KfeAuditLatestResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAuditLatestResponse.java)

Campos observados no DTO:

- `KfeAuditEventResponse latestEvent`
- `KfeAuditRootResponse root`

### `KfeAuditRootResponse`

Fonte: [KfeAuditRootResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAuditRootResponse.java)

Campos observados no DTO:

- `String merkleRoot`
- `long eventCount`
- `Long fromSequence`
- `Long toSequence`
- `LocalDateTime generatedAt`

### `KfeColdWalletPsbtRequest`

Fonte: [KfeColdWalletPsbtRequest.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeColdWalletPsbtRequest.java)

Campos observados no DTO:

- `@NotBlank @Size(max = 128) String destinationAddress`
- `@Min(546) long amountSats`
- `@Min(1) Integer confirmationTarget`
- `@Min(1) Long feeRateSatsPerVbyte`
- `@Valid List<Input> inputs`

### `KfeColdWalletPsbtResponse`

Fonte: [KfeColdWalletPsbtResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeColdWalletPsbtResponse.java)

Campos observados no DTO:

- `String psbt`
- `String psbtHash`
- `long feeSats`
- `long amountSats`
- `String destinationAddress`
- `List<KfeColdWalletPsbtRequest.Input> inputs`

### `KfeCreateWalletRequest`

Fonte: [KfeCreateWalletRequest.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeCreateWalletRequest.java)

Campos observados no DTO:

- `@NotNull KfeWalletKind kind`
- `KfeWalletName name`
- `@Size(max = 96) String label`
- `String xpub`
- `String descriptor`
- `String fingerprint`
- `String derivationPath`
- `String initialAddress`
- `String initialAddressDerivationPath`
- `Integer initialAddressDerivationIndex`
- `String initialAddressProviderReference`
- `Boolean issueInitialAddress`

### `KfeDashboardResponse`

Fonte: [KfeDashboardResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeDashboardResponse.java)

Campos observados no DTO:

- `List<KfeDashboardWallet> wallets`
- `List<KfeStatementItem> recentStatement`
- `long totalSpendableSats`
- `long totalObservedSats`
- `long totalVisibleSats`

### `KfeReceivingCapabilitiesResponse`

Fonte: [KfeReceivingCapabilitiesResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeReceivingCapabilitiesResponse.java)

Campos observados no DTO:

- `boolean canReceiveInternal`
- `boolean canReceiveLightning`
- `boolean canReceiveOnchain`
- `String preferredRail`
- `List<String> missingRequirements`
- `String receiverDisplayName`
- `UUID internalWalletId`
- `List<String> availableRails`
- `Limits limits`

### `KfeSubmitTransactionRequest`

Fonte: [KfeSubmitTransactionRequest.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeSubmitTransactionRequest.java)

Campos observados no DTO:

- `@NotBlank String idempotencyKey`
- `@NotNull KfeRail rail`
- `@NotNull KfeDirection direction`
- `UUID sourceWalletId`
- `UUID destinationWalletId`
- `@Min(1) long amountSats`
- `@Min(0) long networkFeeSats`
- `String externalReference`
- `String memo`
- `String totpCode`
- `String passkeyAssertionJson`
- `String confirmationPassphrase`

### `KfeTransactionResponse`

Fonte: [KfeTransactionResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeTransactionResponse.java)

Campos observados no DTO:

- `UUID id`
- `KfeTransactionStatus status`
- `KfeRail rail`
- `KfeDirection direction`
- `UUID sourceWalletId`
- `UUID destinationWalletId`
- `long grossAmountSats`
- `long receiverAmountSats`
- `long networkFeeSats`
- `long keroseneFeeSats`
- `long totalDebitSats`
- `String quorumProposalHash`
- `int quorumAckCount`
- `String providerReference`
- `String blockchainTxid`
- `String failureCode`
- `String failureMessage`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`

### `KfeUtxoResponse`

Fonte: [KfeUtxoResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeUtxoResponse.java)

Campos observados no DTO:

- `String txid`
- `int vout`
- `long valueSats`
- `String scriptPubKey`
- `String address`

### `KfeWalletNameOption`

Fonte: [KfeWalletNameOption.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeWalletNameOption.java)

Campos observados no DTO:

- `KfeWalletName name`
- `String label`

### `KfeWalletResponse`

Fonte: [KfeWalletResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeWalletResponse.java)

Campos observados no DTO:

- `UUID id`
- `KfeWalletKind kind`
- `KfeWalletStatus status`
- `String label`
- `String walletName`
- `String walletTypeDescription`
- `String asset`
- `boolean spendable`
- `boolean xpubConfigured`
- `boolean mpcKeyConfigured`
- `String activeAddress`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
