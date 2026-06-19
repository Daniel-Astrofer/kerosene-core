# Bitcoin Accounts API

Documentação corporativa da antiga família de contas Bitcoin e dos substitutos ativos no KFE.

Fonte real inspecionada:

- Lista atual de controllers em `backend/kerosene/src/main/java/**`.
- `backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java`.
- `backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java`.
- `backend/kerosene/src/main/java/source/common/security/EndpointPolicyRegistry.java`.

## Estado real do serviço

A documentação anterior citava `BitcoinAccountsController`, mas esse controller **não existe** no código-fonte atual. Portanto, `/bitcoin/**` não deve ser documentado como API ativa.

O fluxo Bitcoin ativo está em KFE:

| Caso de uso | Endpoint ativo | Método | Documento canônico |
| --- | --- | --- | --- |
| Criar carteira interna/custodial/cold | `/kfe/wallets` | `POST` | `KFE.md` |
| Listar carteiras | `/kfe/wallets` | `GET` | `KFE.md` |
| Dashboard de saldo total e por carteira | `/kfe/dashboard` | `GET` | `KFE.md` |
| Criar/rotacionar endereço de recebimento | `/kfe/wallets/{walletId}/addresses/rotate` | `POST` | `KFE.md` |
| Listar UTXOs cold wallet | `/kfe/wallets/{walletId}/utxos` | `GET` | `KFE.md` |
| Criar PSBT cold wallet | `/kfe/wallets/{walletId}/cold-wallet/psbt` | `POST` | `KFE.md` |
| Quote de transação | `/kfe/transactions/quote` | `POST` | `KFE.md` |
| Submeter transação | `/kfe/transactions` | `POST` | `KFE.md` |

## Headers do fluxo ativo

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
| --- | --- | --- | --- | --- |
| `Authorization` | string | Sim | JWT Bearer do usuário. | `Bearer <JWT>` |
| `Content-Type` | string | Sim em `POST` | JSON. | `application/json` |
| `Accept` | string | Opcional | JSON. | `application/json` |
| `X-Correlation-Id` | string | Recomendado | Rastreabilidade. | `btc-20260619-0001` |

## Endpoint ativo: Criar carteira Bitcoin via KFE

```http
POST /kfe/wallets
```

### O que faz

Cria uma carteira Bitcoin KFE por método de custódia: `INTERNAL`, `CUSTODIAL_ONCHAIN` ou `WATCH_ONLY`.

### Regras importantes

- O backend limita uma carteira ativa/em criação por usuário e por `kind`.
- `WATCH_ONLY` deve receber material público (`xpub` ou `descriptor`) para funcionar corretamente.
- `issueInitialAddress` só deve ser usado quando o backend consegue emitir endereço para aquele tipo.

### Request body

| Campo | Tipo | Obrigatório | Nullable | Default | Validações | Descrição | Exemplo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | enum | Sim | Não | nenhum | `INTERNAL`, `CUSTODIAL_ONCHAIN`, `WATCH_ONLY` | Método de custódia. | `WATCH_ONLY` |
| `name` | enum | Não | Sim | nenhum | `INVESTMENT`, `DAILY`, `VEHICLE`, `FUTURE_EXPENSES` | Nome controlado. | `INVESTMENT` |
| `label` | string | Não | Sim | nenhum | máximo 96 | Rótulo exibível. | `Cold Ledger` |
| `xpub` | string | Condicional | Sim | nenhum | material público Bitcoin | XPUB para watch-only/cold. | `xpub6...` |
| `descriptor` | string | Não | Sim | nenhum | descriptor Bitcoin | Política/script de carteira. | `wpkh([f23ab...]xpub...)` |
| `fingerprint` | string | Não | Sim | nenhum | fingerprint de chave | Master fingerprint. | `f23ab912` |
| `derivationPath` | string | Não | Sim | nenhum | BIP path | Caminho de derivação. | `m/84'/0'/0'` |
| `issueInitialAddress` | boolean | Não | Não | `false` | boolean | Solicita endereço inicial. | `true` |

### Exemplo curl

```bash
curl -X POST "$BASE_URL/kfe/wallets" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "WATCH_ONLY",
    "label": "Cold wallet",
    "xpub": "xpub6...",
    "fingerprint": "f23ab912",
    "derivationPath": "m/84h/0h/0h",
    "issueInitialAddress": true
  }'
```

### Response de sucesso

Status: `201 Created`

```json
{
  "success": true,
  "message": "KFE wallet created.",
  "data": {
    "id": "018f5d42-7b46-7d9f-9a1b-c405c8d6e020",
    "kind": "WATCH_ONLY",
    "name": null,
    "label": "Cold wallet",
    "activeAddress": "bc1q...",
    "balanceSats": 0,
    "availableBalanceSats": 0,
    "status": "ACTIVE",
    "createdAt": "2026-06-19T12:00:00",
    "updatedAt": "2026-06-19T12:00:00"
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

## Endpoint ativo: Listar UTXOs de cold wallet

```http
GET /kfe/wallets/{walletId}/utxos
```

### O que faz

Retorna UTXOs associados a uma carteira watch-only/cold para construção de PSBT.

### Path parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
| --- | --- | --- | --- | --- |
| `walletId` | UUID | Sim | Carteira cold/watch-only do usuário. | `018f5d42-7b46-7d9f-9a1b-c405c8d6e020` |

### Response de sucesso

```json
{
  "success": true,
  "message": "KFE wallet UTXOs retrieved.",
  "data": [
    {
      "txid": "5e3f...",
      "vout": 0,
      "valueSats": 25000,
      "address": "bc1q...",
      "confirmations": 6
    }
  ],
  "timestamp": "2026-06-19T12:00:00"
}
```

## Endpoint ativo: Criar PSBT de cold wallet

```http
POST /kfe/wallets/{walletId}/cold-wallet/psbt
```

### Request body

| Campo | Tipo | Obrigatório | Descrição | Exemplo |
| --- | --- | --- | --- | --- |
| `destinationAddress` | string | Sim | Endereço Bitcoin destino. | `bc1q...` |
| `amountSats` | long | Sim | Valor em satoshis. | `10000` |
| `feeRateSatsPerVbyte` | long/decimal | Sim | Fee rate para montagem da PSBT. | `12` |
| `inputs` | array | Não | UTXOs escolhidos manualmente. | `[{"txid":"...","vout":0}]` |

### Response de sucesso

```json
{
  "success": true,
  "message": "KFE cold wallet PSBT created.",
  "data": {
    "psbt": "cHNidP8BA...",
    "psbtHash": "sha256:...",
    "feeSats": 1200,
    "amountSats": 10000,
    "destinationAddress": "bc1q..."
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

## Rotas legadas removidas

As rotas `REMOVED_LEGACY_FINANCIAL_ROUTE`, `REMOVED_LEGACY_FINANCIAL_ROUTE`, `REMOVED_LEGACY_FINANCIAL_ROUTE`, `REMOVED_LEGACY_FINANCIAL_ROUTE` e `REMOVED_LEGACY_FINANCIAL_ROUTE` dependiam de `BitcoinAccountsController`, que não existe no código atual. Trate-as como `STALE`.

## PSBT workflow KFE ativo

A criação de PSBT de cold wallet agora é KFE-native e gera workflow persistido. Use os endpoints abaixo em vez do package legado `source.bitcoinaccounts`.

| Método | Path | Auth | Para que serve |
| --- | --- | --- | --- |
| `GET` | `/kfe/wallets/{walletId}/utxos` | `AUTHENTICATED` | Lista UTXOs da carteira watch-only/cold wallet. |
| `POST` | `/kfe/wallets/{walletId}/cold-wallet/psbt` | `AUTHENTICATED` | Cria PSBT e workflow KFE. |
| `GET` | `/api/admin/kfe/reserves/psbts` | `ADMIN` | Lista workflows, com query opcional `walletId`. |
| `GET` | `/api/admin/kfe/reserves/psbts/{workflowId}` | `ADMIN` | Consulta um workflow. |
| `POST` | `/api/admin/kfe/reserves/psbts/{workflowId}/signed` | `ADMIN` | Anexa payload PSBT assinado. |
| `POST` | `/api/admin/kfe/reserves/psbts/{workflowId}/broadcast` | `ADMIN` | Faz broadcast do workflow finalizado. |

`KfePsbtWorkflowResponse` retorna `id`, `userId`, `walletId`, `status`, `psbt`, `psbtHash`, `signedPsbtHash`, `rawTxHash`, `broadcastTxid`, `amountSats`, `feeSats`, `destinationAddress`, `inputs`, `failureMessage`, `createdAt`, `updatedAt`, `signedAt` e `broadcastAt`.

Status possíveis do workflow: `CREATED`, `SIGNED`, `FINALIZED`, `BROADCAST`, `FAILED`.

## Status codes

| Status | Quando ocorre | Como resolver |
| --- | --- | --- |
| `200 OK` | Listagens, UTXOs, rotação ou PSBT retornados. | Consumir `data`. |
| `201 Created` | Carteira criada. | Persistir `data.id`. |
| `400 Bad Request` | Body inválido, enum inválido, UUID inválido. | Corrigir payload/path. |
| `401 Unauthorized` | JWT ausente/inválido. | Reautenticar. |
| `403 Forbidden` | Carteira de outro usuário ou rota legada sem controller. | Usar token/rota ativa correta. |
| `404 Not Found` | Carteira inexistente ou rota legada removida. | Conferir ID/path. |
| `409 Conflict` | Já existe carteira ativa para o mesmo `kind`. | Usar carteira existente. |
| `422 Unprocessable Entity` | Material público ausente ou regra de custódia não satisfeita. | Corrigir `xpub`/`descriptor`/kind. |
| `500 Internal Server Error` | Falha inesperada de serviço. | Investigar logs. |
