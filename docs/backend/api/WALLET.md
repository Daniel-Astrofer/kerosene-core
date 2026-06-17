# Wallet API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `5`.

Controllers cobertos:

- `WalletController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/wallet/all` | `WalletController.getAllWallets` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ApiResponse<List<WalletResponseDTO>>` | [WalletController.java](../../../backend/kerosene/src/main/java/source/wallet/controller/WalletController.java#L44) |
| `POST` | `/wallet/create` | `WalletController.create` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: WalletRequestDTO | `ApiResponse<WalletResponseDTO>` | [WalletController.java](../../../backend/kerosene/src/main/java/source/wallet/controller/WalletController.java#L35) |
| `DELETE` | `/wallet/delete` | `WalletController.deleteWallets` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: WalletRequestDTO | `ApiResponse<String>` | [WalletController.java](../../../backend/kerosene/src/main/java/source/wallet/controller/WalletController.java#L66) |
| `GET` | `/wallet/find` | `WalletController.getWalletByName` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: name: String | `ApiResponse<WalletResponseDTO>` | [WalletController.java](../../../backend/kerosene/src/main/java/source/wallet/controller/WalletController.java#L51) |
| `PUT` | `/wallet/update` | `WalletController.updateWallet` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: WalletUpdateDTO | `ApiResponse<String>` | [WalletController.java](../../../backend/kerosene/src/main/java/source/wallet/controller/WalletController.java#L59) |

## DTOs e Payloads

### `WalletRequestDTO`

Fonte: [WalletRequestDTO.java](../../../backend/kerosene/src/main/java/source/wallet/dto/WalletRequestDTO.java)

Campos observados no DTO:

- `@NotBlank(message = "A passphrase é obrigatória") char[] passphrase`
- `@NotBlank(message = "O nome da carteira é obrigatório") @Size(min = 3, max = 50, message = "O nome deve ter entre 3 e 50 caracteres") String name`
- `String xpub`
- `String walletMode`

### `WalletResponseDTO`

Fonte: [WalletResponseDTO.java](../../../backend/kerosene/src/main/java/source/wallet/dto/WalletResponseDTO.java)

Campos observados no DTO:

- `Long id`
- `String name`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`
- `Boolean isActive`
- `String totpUri`
- `String depositAddress`
- `String lightningAddress`
- `String walletMode`
- `Boolean xpubConfigured`
- `String cardType`
- `String cardHolderName`
- `String cardMaskedNumber`
- `String cardNumberSuffix`
- `Integer cardSequence`
- `String cardRotationStatus`
- `LocalDateTime cardIssuedAt`
- `LocalDateTime cardExpiresAt`
- `LocalDateTime cardNextRotationAt`
- `LocalDateTime cardLastRotatedAt`
- `String previousCardNumberSuffix`
- `LocalDateTime previousCardExpiresAt`
- `BigDecimal withdrawalFeeRate`
- `BigDecimal depositFeeRate`

### `WalletUpdateDTO`

Fonte: [WalletUpdateDTO.java](../../../backend/kerosene/src/main/java/source/wallet/dto/WalletUpdateDTO.java)

Campos observados no DTO:

- `@NotBlank(message = "A passphrase é obrigatória para autorizar a modificação") char[] passphrase`
- `@NotBlank(message = "O nome atual da carteira é obrigatório") String name`
- `@Size(min = 3, max = 50, message = "O novo nome deve ter entre 3 e 50 caracteres") String newName`
- `String newXpub`
- `String newWalletMode`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
