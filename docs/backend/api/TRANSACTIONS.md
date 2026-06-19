# Transactions, Network e Economy API

Documentação corporativa dos endpoints de transações/economia que existem no backend atual.

Fonte real inspecionada:

- `backend/kerosene/src/main/java/source/common/controller/EconomyController.java`
- `backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java`
- `backend/kerosene/src/main/java/source/common/security/EndpointPolicyRegistry.java`

## Estado real do serviço

Os controllers legados `TransactionController`, `NetworkPaymentsController`, `DepositController`, `OnrampController` e `BlockchainVisualizationController` não existem no código-fonte atual. O backend ativo expõe:

| Família | Endpoints ativos | Auth |
| --- | --- | --- |
| Economy | `GET /api/economy/status`, `GET /api/economy/btc-price` | `AUTHENTICATED` |
| KFE Transactions | `POST /kfe/transactions`, `POST /kfe/transactions/quote`, `GET /kfe/transactions/{transactionId}` | `AUTHENTICATED` |

## Headers comuns

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
| --- | --- | --- | --- | --- |
| `Authorization` | string | Sim | JWT Bearer. | `Bearer <JWT>` |
| `Content-Type` | string | Sim em `POST` | JSON. | `application/json` |
| `Accept` | string | Opcional | JSON. | `application/json` |
| `X-Correlation-Id` | string | Recomendado | Rastreabilidade. | `tx-20260619-0001` |

## Endpoint: Status econômico

```http
GET /api/economy/status
```

### O que faz

Consulta status econômico/plataforma em Redis: taxa de saque atual e status de saques.

### Quando usar

- Mostrar status de liquidez/saques em telas administrativas ou app.
- Verificar se saques estão habilitados antes de iniciar fluxo financeiro.
- Exibir a taxa atual de saque em satoshis.

### Request

Não recebe body, path parameters ou query parameters.

### Response de sucesso

Status: `200 OK`

```json
{
  "success": true,
  "message": "Current platform liquidity and economy status retrieved.",
  "data": {
    "withdrawalFeeSats": 10000,
    "withdrawalStatus": "ENABLED"
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `withdrawalFeeSats` | long | Valor de `economy:current_withdrawal_fee` no Redis; default `10000`. |
| `withdrawalStatus` | string | Valor de `system:status:withdrawals` no Redis; default `ENABLED`. |

### Exemplo curl

```bash
curl -X GET "$BASE_URL/api/economy/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Accept: application/json"
```

## Endpoint: Cotação BTC

```http
GET /api/economy/btc-price
```

### O que faz

Consulta `TickerService` para BTC/USD e BTC/BRL e calcula USD/BRL derivado quando BTC/USD for maior que zero.

### Quando usar

- Exibir cotação no app.
- Calcular estimativas visuais de conversão.
- Preparar UI antes de quote financeiro KFE. Para valores finais de transação, use `/kfe/transactions/quote`.

### Request

Não recebe body, path parameters ou query parameters.

### Response de sucesso

Status: `200 OK`

```json
{
  "success": true,
  "message": "Current BTC market prices retrieved.",
  "data": {
    "btcUsd": 65000.00,
    "btcBrl": 351000.00,
    "usdBrl": 5.40000000
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `btcUsd` | decimal | Preço BTC em USD retornado pelo `TickerService`. |
| `btcBrl` | decimal | Preço BTC em BRL retornado pelo `TickerService`. |
| `usdBrl` | decimal | `btcBrl / btcUsd`, escala 8, `HALF_UP`; zero se `btcUsd` for zero. |

### Exemplo curl

```bash
curl -X GET "$BASE_URL/api/economy/btc-price" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Accept: application/json"
```

## Endpoints KFE de transação

Os endpoints abaixo são documentados em detalhe em `KFE.md` e são os substitutos oficiais das rotas legadas:

| Método | Path | Para que serve |
| --- | --- | --- |
| `POST` | `/kfe/transactions/quote` | Simular pagamento e fees antes da submissão. |
| `POST` | `/kfe/transactions` | Criar/submeter transação KFE com idempotência. |
| `GET` | `/kfe/transactions/{transactionId}` | Consultar status e detalhes da transação. |

## Rotas legadas removidas

As famílias abaixo eram citadas por documentação antiga, mas os controllers correspondentes não existem no código-fonte atual:

| Família | Estado atual | Substituto |
| --- | --- | --- |
| `/transactions/**` | Controller ausente; trate como `STALE`. | `/kfe/transactions/**` |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | Controller ausente. | KFE wallets/transactions. |
| `/api/onramp/**` | Controller ausente. | Integração futura com controller/policy próprios. |
| `/transactions/network/**` | Controller ausente. | KFE rails. |
| `/transactions/visualization/**` | Controller ausente. | Nenhum endpoint ativo confirmado. |

## Status codes

| Status | Quando ocorre | Como resolver | Exemplo de resposta |
| --- | --- | --- | --- |
| `200 OK` | Status/cotação retornados ou KFE concluiu a operação. | Consumir `data`. | `{ "success": true, "data": {} }` |
| `400 Bad Request` | Body/query inválido em endpoints KFE. | Corrigir payload. | Varia. |
| `401 Unauthorized` | JWT ausente ou inválido. | Reautenticar. | Varia. |
| `403 Forbidden` | Rota legada inexistente/sem policy ou token insuficiente. | Usar endpoints ativos. | Varia. |
| `404 Not Found` | Controller legado ausente ou transação não encontrada. | Conferir path/ID. | Varia. |
| `409 Conflict` | Idempotência/conflito de transação KFE. | Consultar transação existente. | Varia. |
| `422 Unprocessable Entity` | Regra financeira KFE não satisfeita. | Ajustar saldo, rail ou estado. | Varia. |
| `503 Service Unavailable` | Redis/ticker/dependência indisponível. | Retry com backoff. | Varia. |
