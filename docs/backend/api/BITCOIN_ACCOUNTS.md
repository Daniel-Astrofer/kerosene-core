# Bitcoin Accounts API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `18`.

Controllers cobertos:

- `BitcoinAccountsController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/bitcoin/accounts` | `BitcoinAccountsController.list` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ApiResponse<List<Map<String, Object>>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L51) |
| `POST` | `/bitcoin/accounts/cold-wallet` | `BitcoinAccountsController.createColdWallet` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: CreateColdWalletRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L67) |
| `POST` | `/bitcoin/accounts/internal-card` | `BitcoinAccountsController.createInternalCard` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: CreateInternalCardRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L58) |
| `GET` | `/bitcoin/accounts/{accountId}/receive-requests` | `BitcoinAccountsController.listReceiveRequests` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: accountId: UUID | `ApiResponse<List<Map<String, Object>>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L99) |
| `POST` | `/bitcoin/accounts/{accountId}/receive-requests` | `BitcoinAccountsController.createReceiveRequest` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: accountId: UUID<br>body: CreateReceiveRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L84) |
| `GET` | `/bitcoin/cold-wallets/{coldWalletId}/psbt` | `BitcoinAccountsController.listColdWalletPsbt` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: coldWalletId: UUID | `ApiResponse<List<Map<String, Object>>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L177) |
| `POST` | `/bitcoin/cold-wallets/{coldWalletId}/psbt` | `BitcoinAccountsController.createPsbt` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: coldWalletId: UUID<br>body: CreatePsbtRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L152) |
| `GET` | `/bitcoin/cold-wallets/{coldWalletId}/utxos` | `BitcoinAccountsController.listColdWalletUtxos` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: coldWalletId: UUID | `ApiResponse<List<Map<String, Object>>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L168) |
| `GET` | `/bitcoin/psbt/{workflowId}` | `BitcoinAccountsController.getPsbt` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: workflowId: UUID | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L200) |
| `POST` | `/bitcoin/psbt/{workflowId}/signed` | `BitcoinAccountsController.submitSignedPsbt` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: workflowId: UUID<br>body: SubmitSignedPsbtRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L186) |
| `POST` | `/bitcoin/receive-requests/{id}/expire` | `BitcoinAccountsController.expireReceiveRequest` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: id: UUID | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L133) |
| `POST` | `/bitcoin/receive-requests/{id}/hide` | `BitcoinAccountsController.hideReceiveRequest` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: id: UUID | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L124) |
| `GET` | `/bitcoin/receive-requests/{id}/status` | `BitcoinAccountsController.receiveStatus` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: id: UUID | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L115) |
| `POST` | `/bitcoin/receive-requests/{id}/user-action` | `BitcoinAccountsController.receiveUserAction` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: id: UUID<br>body: UserActionRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L142) |
| `GET` | `/bitcoin/receive/{publicCode}` | `BitcoinAccountsController.publicReceive` | PUBLIC<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: publicCode: String | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L108) |
| `GET` | `/bitcoin/tax-events` | `BitcoinAccountsController.listTaxEvents` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ApiResponse<List<Map<String, Object>>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L209) |
| `GET` | `/bitcoin/tax-events/export` | `BitcoinAccountsController.exportTaxEvents` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: format: String | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L216) |
| `POST` | `/bitcoin/tax-events/{eventId}/classify` | `BitcoinAccountsController.classifyTaxEvent` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: eventId: UUID<br>body: ClassifyTaxEventRequest | `ApiResponse<Map<String, Object>>` | [BitcoinAccountsController.java](../../../backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java#L225) |

## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
