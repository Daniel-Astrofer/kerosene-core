# Payments API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `4`.

Controllers cobertos:

- `PaymentsController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `POST` | `/payments/quote` | `PaymentsController.quote` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: PaymentQuoteRequest | `ApiResponse<PaymentQuoteResponse>` | [PaymentsController.java](../../../backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java#L43) |
| `GET` | `/payments/{paymentIntentId}` | `PaymentsController.status` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: paymentIntentId: UUID | `ApiResponse<PaymentStatusResponse>` | [PaymentsController.java](../../../backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java#L63) |
| `POST` | `/payments/{paymentIntentId}/confirm` | `PaymentsController.confirm` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: paymentIntentId: UUID<br>body: PaymentConfirmRequest | `ApiResponse<PaymentStatusResponse>` | [PaymentsController.java](../../../backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java#L51) |
| `GET` | `/users/{receiverIdentifier}/receiving-capabilities` | `PaymentsController.receivingCapabilities` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: receiverIdentifier: String | `ApiResponse<ReceivingCapabilitiesResponse>` | [PaymentsController.java](../../../backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java#L71) |

## DTOs e Payloads

### `PaymentConfirmRequest`

Fonte: [PaymentConfirmRequest.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentConfirmRequest.java)

Campos observados no DTO:

- `@NotBlank @Size(max = 128) String idempotencyKey`
- `@Size(max = 512) String userConfirmationToken`
- `@NotNull Long acceptedTotalDebitSats`
- `@NotNull Long acceptedReceiverAmountSats`

### `PaymentQuoteRequest`

Fonte: [PaymentQuoteRequest.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentQuoteRequest.java)

Campos observados no DTO:

- `@NotNull PaymentEnums.PaymentRail rail`
- `@NotNull PaymentEnums.FeeMode feeMode`
- `@NotBlank @Size(max = 40) String amountFiat`
- `@NotBlank @Size(max = 8) String fiatCurrency`
- `@NotBlank @Size(max = 16) String asset`
- `@Size(max = 255) String receiverIdentifier`
- `@Size(max = 2048) String externalDestination`
- `PaymentEnums.OnchainSpeed speed`

### `PaymentQuoteResponse`

Fonte: [PaymentQuoteResponse.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentQuoteResponse.java)

Campos observados no DTO:

- `UUID paymentIntentId`
- `Instant quoteExpiresAt`
- `PaymentEnums.PaymentRail rail`
- `PaymentEnums.FeeMode feeMode`
- `String receiverDisplayName`
- `String receiverAmountFiat`
- `long receiverAmountSats`
- `String totalDebitFiat`
- `long totalDebitSats`
- `String networkFeeFiat`
- `long networkFeeSats`
- `String keroseneFeeFiat`
- `long keroseneFeeSats`
- `List<String> warnings`
- `boolean requiresConfirmation`

### `PaymentStatusResponse`

Fonte: [PaymentStatusResponse.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentStatusResponse.java)

Campos observados no DTO:

- `UUID paymentIntentId`
- `PaymentEnums.PaymentIntentStatus status`
- `PaymentEnums.PaymentRail rail`
- `PaymentEnums.FeeMode feeMode`
- `String receiverDisplayName`
- `long receiverAmountSats`
- `long totalDebitSats`
- `long networkFeeSats`
- `long keroseneFeeSats`
- `Instant quoteExpiresAt`
- `String failureCode`
- `String failureMessage`
- `List<String> warnings`

### `ReceivingCapabilitiesResponse`

Fonte: [ReceivingCapabilitiesResponse.java](../../../backend/kerosene/src/main/java/source/payments/dto/ReceivingCapabilitiesResponse.java)

Campos observados no DTO:

- `boolean canReceiveInternal`
- `boolean canReceiveLightning`
- `boolean canReceiveOnchain`
- `PaymentEnums.PaymentRail preferredRail`
- `List<String> missingRequirements`
- `String receiverDisplayName`
- `List<PaymentEnums.PaymentRail> availableRails`
- `Limits limits`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
