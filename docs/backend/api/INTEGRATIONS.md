# Integracoes API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `1`.

Controllers cobertos:

- `BtcPayWebhookController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `POST` | `/integrations/btcpay/webhook/{storeId}` | `BtcPayWebhookController.receiveWebhook` | PUBLIC<br>cond: `@ConditionalOnProperty(prefix = "btcpay", name = "enabled", havingValue = "true")` | path: storeId: String<br>body: rawBody: String | `ResponseEntity<Void>` | [BtcPayWebhookController.java](../../../backend/kerosene/src/main/java/source/transactions/controller/BtcPayWebhookController.java#L25) |

## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
