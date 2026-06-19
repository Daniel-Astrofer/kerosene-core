# Wallet API

Documentação corporativa da antiga família `/wallet/**` e dos endpoints ativos de carteira em KFE.

Fonte real inspecionada:

- Lista atual de controllers em `backend/kerosene/src/main/java/**`.
- `backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java`.
- `backend/kerosene/src/main/java/source/kfe/controller/KfeDashboardController.java`.
- `backend/kerosene/src/main/java/source/common/security/EndpointPolicyRegistry.java`.

## Estado real do serviço

`WalletController` legado não existe no código-fonte atual e `/wallet/**` não tem policy no `EndpointPolicyRegistry`. A API ativa de carteiras é a KFE.

## Endpoints ativos de carteira

| Método | Path | Para que serve | Auth |
| --- | --- | --- | --- |
| `POST` | `/kfe/wallets` | Criar carteira por método de custódia. | `AUTHENTICATED` |
| `GET` | `/kfe/wallets` | Listar carteiras do usuário. | `AUTHENTICATED` |
| `GET` | `/kfe/wallets/names` | Listar opções de nomes controlados. | `AUTHENTICATED` |
| `POST` | `/kfe/wallets/{walletId}/addresses/rotate` | Emitir/rotacionar endereço de recebimento. | `AUTHENTICATED` |
| `GET` | `/kfe/wallets/{walletId}/utxos` | Listar UTXOs de cold wallet. | `AUTHENTICATED` |
| `POST` | `/kfe/wallets/{walletId}/cold-wallet/psbt` | Criar PSBT para cold wallet. | `AUTHENTICATED` |
| `GET` | `/kfe/dashboard` | Obter saldo total, carteiras e extrato. | `AUTHENTICATED` |

## Headers comuns

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
| --- | --- | --- | --- | --- |
| `Authorization` | string | Sim | JWT Bearer do usuário. | `Bearer <JWT>` |
| `Content-Type` | string | Sim em `POST` | JSON. | `application/json` |
| `Accept` | string | Opcional | JSON. | `application/json` |
| `X-Correlation-Id` | string | Recomendado | Auditoria e troubleshooting. | `wallet-20260619-0001` |

## Endpoint: Criar carteira

```http
POST /kfe/wallets
```

### O que faz

Cria uma carteira KFE para o usuário autenticado.

### Regras de negócio

- Só deve existir uma carteira ativa/em criação por `kind` para cada usuário.
- `WATCH_ONLY` representa cold/watch-only e deve usar material público.
- Update/delete legados não existem no KFE atual; ações de arquivar/renomear exigem endpoint novo.

### Request body

| Campo | Tipo | Obrigatório | Nullable | Default | Validações | Descrição | Exemplo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `kind` | enum | Sim | Não | nenhum | `INTERNAL`, `CUSTODIAL_ONCHAIN`, `WATCH_ONLY` | Método de custódia. | `INTERNAL` |
| `name` | enum | Não | Sim | nenhum | `INVESTMENT`, `DAILY`, `VEHICLE`, `FUTURE_EXPENSES` | Nome controlado. | `DAILY` |
| `label` | string | Não | Sim | nenhum | máximo 96 | Rótulo exibível. | `Carteira diária` |
| `xpub` | string | Condicional | Sim | nenhum | material público | Usado em watch-only/cold. | `xpub6...` |
| `descriptor` | string | Não | Sim | nenhum | descriptor Bitcoin | Política/script. | `wpkh(...)` |
| `fingerprint` | string | Não | Sim | nenhum | fingerprint | Fingerprint da master key. | `f23ab912` |
| `derivationPath` | string | Não | Sim | nenhum | BIP path | Caminho de derivação. | `m/84h/0h/0h` |
| `issueInitialAddress` | boolean | Não | Não | `false` | boolean | Solicita endereço inicial. | `false` |

### Exemplo curl

```bash
curl -X POST "$BASE_URL/kfe/wallets" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"kind":"INTERNAL","label":"Carteira global","issueInitialAddress":false}'
```

### Response de sucesso

Status: `201 Created`

```json
{
  "success": true,
  "message": "KFE wallet created.",
  "data": {
    "id": "018f5d42-7b46-7d9f-9a1b-c405c8d6e020",
    "kind": "INTERNAL",
    "name": null,
    "label": "Carteira global",
    "activeAddress": null,
    "balanceSats": 0,
    "availableBalanceSats": 0,
    "status": "ACTIVE",
    "createdAt": "2026-06-19T12:00:00",
    "updatedAt": "2026-06-19T12:00:00"
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

## Endpoint: Listar carteiras

```http
GET /kfe/wallets
```

### O que faz

Retorna as carteiras KFE do usuário autenticado.

### Exemplo curl

```bash
curl -X GET "$BASE_URL/kfe/wallets" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Accept: application/json"
```

### Response de sucesso

Status: `200 OK`; `data` é uma lista de `KfeWalletResponse` com os campos do exemplo de criação.

## Endpoint: Dashboard de carteiras

```http
GET /kfe/dashboard
```

### O que faz

Retorna saldo total, saldo disponível, pendências, lista de carteiras e statement. A Home do frontend deve usar este endpoint para montar o primeiro card de saldo total e os cards seguintes por carteira.

### Response de sucesso

```json
{
  "success": true,
  "message": "KFE dashboard retrieved.",
  "data": {
    "totalBalanceSats": 150000,
    "availableBalanceSats": 149000,
    "pendingBalanceSats": 1000,
    "wallets": [],
    "statement": []
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

## Rotas legadas removidas

| Rota antiga | Estado | Substituto |
| --- | --- | --- |
| `GET /wallet/all` | `STALE`; controller ausente. | `GET /kfe/wallets` ou `GET /kfe/dashboard` |
| `POST /wallet/create` | `STALE`; controller ausente. | `POST /kfe/wallets` |
| `DELETE /wallet/delete` | `STALE`; controller ausente. | Não há equivalente KFE atual. |
| `GET /wallet/find` | `STALE`; controller ausente. | Filtrar `GET /kfe/wallets`. |
| `PUT /wallet/update` | `STALE`; controller ausente. | Não há equivalente KFE atual. |

## Lifecycle ativo de wallet KFE

O controller KFE agora expõe update e archive diretamente, sem reativar `/wallet/**`.

| Método | Path | Auth | Para que serve |
| --- | --- | --- | --- |
| `PATCH` | `/kfe/wallets/{walletId}` | `AUTHENTICATED` | Atualiza metadados editáveis da carteira, atualmente `label`. |
| `POST` | `/kfe/wallets/{walletId}/archive` | `AUTHENTICATED` | Arquiva a carteira no lifecycle KFE. |

`PATCH /kfe/wallets/{walletId}` recebe `KfeUpdateWalletRequest` com `label` opcional, nullable e limitado a 96 caracteres. Ambos retornam `ApiResponse<KfeWalletResponse>`.

```bash
curl -X PATCH "$BASE_URL/kfe/wallets/$WALLET_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"label":"Reserva fria"}'
```

```bash
curl -X POST "$BASE_URL/kfe/wallets/$WALLET_ID/archive" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Status codes

| Status | Quando ocorre | Como resolver |
| --- | --- | --- |
| `200 OK` | Listagem/dashboard/rotação/UTXO/PSBT retornados. | Consumir `data`. |
| `201 Created` | Carteira criada. | Persistir `data.id`. |
| `400 Bad Request` | Payload inválido. | Corrigir body/path. |
| `401 Unauthorized` | JWT ausente/inválido. | Reautenticar. |
| `403 Forbidden` | Carteira de outro usuário ou rota legada. | Usar token e rota KFE corretos. |
| `404 Not Found` | Carteira inexistente ou rota legada removida. | Conferir ID/path. |
| `409 Conflict` | Já existe carteira ativa/em criação para o mesmo `kind`. | Usar carteira existente. |
| `422 Unprocessable Entity` | Regra de custódia/material público não satisfeita. | Corrigir request. |
| `500 Internal Server Error` | Falha inesperada. | Investigar logs. |

## Observações de produto

- Para `INTERNAL` e `CUSTODIAL_ONCHAIN`, o frontend deve criar no máximo uma carteira por método de custódia.
- Para `WATCH_ONLY`, o endereço inicial deve ser solicitado na criação quando houver `xpub`/descriptor, porque a rotação posterior pode não ser permitida para esse tipo.
- Fluxos antigos que pediam passphrase/mnemonic local não devem ser confundidos com carteiras KFE remotas.
