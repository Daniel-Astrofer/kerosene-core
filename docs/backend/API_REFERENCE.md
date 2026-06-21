# Kerosene Backend API Reference

Inventário consolidado dos endpoints HTTP ativos do backend Kerosene. O arquivo é mantido para auditoria full-text, conferência de cobertura e busca por rota/controller.

A documentação operacional principal fica separada por domínio em [`docs/backend/api/`](api/README.md). Para integração de produto, frontend, mobile ou QA, comece pelos documentos dessa pasta.

## Como usar esta documentação

A documentação de API do backend é separada em duas camadas:

1. **Documentação operacional por domínio**: os arquivos em [`docs/backend/api/`](api/README.md) são a fonte principal para integração humana, frontend, mobile e QA. Eles explicam finalidade, autenticação, headers, exemplos, status codes e observações de negócio por serviço.
2. **Referência consolidada de auditoria**: este arquivo (`API_REFERENCE.md`) mantém uma visão full-text dos endpoints HTTP ativos para busca, revisão de cobertura e conferência de contratos técnicos.

Para desenvolvimento de produto ou consumo de API, comece por [`docs/backend/api/README.md`](api/README.md). Use este arquivo quando precisar validar se uma rota aparece no inventário consolidado ou fazer auditoria ampla por path/controller.

## Fonte canônica financeira

A superfície financeira ativa é **KFE-only**. Isso significa:

- novos fluxos financeiros devem usar `/kfe/**` e `/api/admin/kfe/**`;
- rotas financeiras legadas não devem ser documentadas como APIs ativas;
- referências históricas a controllers ou DTOs removidos devem ficar somente em arquivos de arquivo/legado, quando necessário;
- [`docs/backend/api/KFE.md`](api/KFE.md) é a documentação operacional principal para carteira, receiving, transaction, quote, payment request, PSBT, reserve, tax e auditoria financeira KFE.

## Mapa da documentação por domínio

| Domínio | Documento principal | Uso recomendado |
| --- | --- | --- |
| Auth, conta, sessão e step-up | [`api/AUTH.md`](api/AUTH.md) | Login, logout, JWT, device-key, PIN, TOTP, backup codes e admin access. |
| KFE financeiro | [`api/KFE.md`](api/KFE.md) | Carteiras, receiving, transações, quote, payment request, PSBT, reserve, tax e auditoria KFE. |
| Auditoria KFE | [`api/AUDIT.md`](api/AUDIT.md) | Endpoints administrativos ativos de auditoria financeira KFE. |
| Wallet legado substituído | [`api/WALLET.md`](api/WALLET.md) | Orientação de migração para endpoints KFE de carteira. |
| Payments legado substituído | [`api/PAYMENTS.md`](api/PAYMENTS.md) | Orientação de migração para receiving/payment requests KFE. |
| Transactions/economy | [`api/TRANSACTIONS.md`](api/TRANSACTIONS.md) | Rotas de economy ativas e ponte para transações KFE. |
| Soberania e quorum | [`api/SOVEREIGNTY.md`](api/SOVEREIGNTY.md) | Status de soberania, quorum e endpoints shard-to-shard. |
| Mining | [`api/MINING.md`](api/MINING.md) | Endpoints ativos de mineração. |
| Notifications | [`api/NOTIFICATIONS.md`](api/NOTIFICATIONS.md) | Registro, leitura e gerenciamento de notificações. |
| Public, health e web | [`api/PUBLIC_HEALTH_WEB.md`](api/PUBLIC_HEALTH_WEB.md) | Health checks, root status, web/admin SPA e actuator documentado. |
| Integrações | [`api/INTEGRATIONS.md`](api/INTEGRATIONS.md) | Webhooks/policies de integrações e lacunas controller/policy. |
| DTOs | [`api/DTO_SCHEMA_INDEX.md`](api/DTO_SCHEMA_INDEX.md) | Índice auxiliar de DTOs; não substitui os documentos por endpoint. |

## Escopo e cobertura deste arquivo

- Seções de endpoint HTTP documentadas: `91` (`90` pares método/path únicos; `GET /` tem variante JSON e HTML por content negotiation).
- Inclui controllers REST atuais, rotas HTML servidas pelo backend e a superfície financeira KFE ativa.
- WebSocket/STOMP e Actuator aparecem em seções próprias porque não são métodos REST de controller de domínio.
- O formato de erro padrão é `ApiResponse` com `success=false`, `message`, `errorCode`, `data` opcional e `timestamp`; filtros também podem retornar erro sem envelope em `413`, `415` e alguns `401/403` do Spring Security.
- Contagem verificada por headings `### <METHOD> <PATH>` neste documento: `91` seções, `90` pares únicos.

## Regras HTTP globais

- Bodies em `POST`, `PUT`, `PATCH` e `DELETE` com corpo precisam usar `Content-Type: application/json`, salvo rotas HTML.
- O filtro paranoico limita o corpo padrao a `2048` bytes; rotas de PSBT aceitam ate `64 KiB`.
- `Digest: SHA-256=<base64>` e opcional, mas se enviado o hash precisa bater com o body.
- `Authorization: Bearer <jwt>` e aceito apenas em rotas protegidas; rotas publicas de auth nao precisam dele.
- JWT proximo da expiracao pode voltar renovado no header `X-New-Token`.
- CORS permite `Authorization`, `Content-Type`, `Digest`, `X-Correlation-Id`, `X-Request-Id`, `X-Idempotency-Key`, `Idempotency-Key`, `X-Admin-Token`, `X-Owner-TOTP`, `X-Hardware-Signature`, headers de release attestation e `X-Device-Hash`.

## Inventário consolidado de endpoints

## Public Web and Health

### GET /

- Controller: `RootStatusController.root` (`backend/kerosene/src/main/java/source/common/controller/RootStatusController.java: 21`).
- Autenticacao: `Publico`.
- Response Java: `Map<String, Object>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "ok",
  "service": "kerosene",
  "region": "DEV",
  "timestamp": "2026-01-01T00:00:00Z",
  "health": "/health/ready",
  "liveness": "/health/live",
  "sovereignty": "/sovereignty/status"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /

- Controller: `WebAdminController.index` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 10`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /admin

- Controller: `WebAdminController.webRoutes` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 15`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /admin/**

- Controller: `WebAdminController.webRoutes` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 15`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Public/Admin API

### GET /api/admin/operations/blockchain

- Controller: `AdminOperationsController.blockchain` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 81`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "primarySource": "BITCOIN_PRUNED_NODE_RPC",
  "network": "mainnet",
  "indexer": "http://bitcoin-indexer:3002",
  "localIndexerConfigured": true,
  "checkedAt": "2026-01-01T00:00:00Z",
  "chain": {
    "height": 840000,
    "headers": 840000,
    "bestBlockHash": "0000000000000000000000000000000000000000000000000000000000000000",
    "chain": "main",
    "difficulty": 1.0,
    "verificationProgress": 1.0,
    "initialBlockDownload": false,
    "pruned": true,
    "prunedRequired": true,
    "pruneHeight": 830000,
    "bestBlockTime": 1704067200,
    "bestBlockTxCount": 1
  },
  "mempool": {
    "transactions": 1,
    "bytes": 250,
    "usage": 1024,
    "minRelayFee": 1e-05,
    "feesSatPerVByte": {
      "fast": 12,
      "halfHour": 8,
      "hour": 5
    }
  },
  "relevantTransactions": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "txidRef": "abcd1234...ef567890",
      "status": "SETTLED",
      "confirmations": 6,
      "network": "BITCOIN",
      "type": "ONCHAIN_WITHDRAWAL",
      "amountBtc": "0.00010000",
      "updatedAt": "2026-01-01T00:00:00"
    }
  ],
  "message": "Bitcoin pruned node is synced"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/health

- Controller: `AdminOperationsController.health` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 76`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `OperationalHealthSnapshot`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "service": "kerosene",
  "region": "DEV",
  "timestamp": "2026-01-01T00:00:00Z",
  "checks": {
    "database": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    },
    "redis": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    }
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/lightning

- Controller: `AdminOperationsController.lightning` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 86`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `LightningNetworkMonitorService.LightningMonitorSnapshot`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "primarySource": "LND_GRPC",
  "checkedAt": "2026-01-01T00:00:00Z",
  "node": {
    "identityPubkey": "02abcdef",
    "alias": "kerosene-lnd",
    "version": "0.18.0",
    "syncedToChain": true,
    "syncedToGraph": true,
    "blockHeight": 840000,
    "blockHash": "0000000000000000000000000000000000000000000000000000000000000000",
    "numPeers": 1,
    "numActiveChannels": 1,
    "numInactiveChannels": 0,
    "numPendingChannels": 0,
    "localBalanceSats": 100000,
    "remoteBalanceSats": 100000,
    "walletConfirmedBalanceSats": 100000
  },
  "message": "LND is synced to chain"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/logs

- Controller: `AdminOperationsController.logs` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 106`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `List<Map<String, Object>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `50` | `int` | false | `50` |

Request body:

Nenhum.

Response body:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000000",
    "createdAt": "2026-01-01T00:00:00",
    "severity": "INFO",
    "eventType": "TRANSFER_SETTLED",
    "reference": "abcd1234",
    "transferRef": "abcd1234",
    "userRef": "abcd1234",
    "payloadRef": "abcd1234"
  }
]
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/metrics

- Controller: `AdminOperationsController.metrics` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 116`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `Map<String, Object>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "checkedAt": "2026-01-01T00:00:00Z",
  "totalVolumeBtc": "0.00020000",
  "totalFeesBtc": "0.00000100",
  "totalTransactions": 2,
  "avgTicketBtc": "0.00010000",
  "confirmedTransactions": 2,
  "pendingTransactions": 0,
  "failedTransactions": 0,
  "transfers": {
    "totalCount": 1,
    "totalVolumeBtc": "0.00010000",
    "totalFeesBtc": "0.00000100",
    "onchainCount": 1,
    "onchainVolumeBtc": "0.00010000",
    "onchainFeesBtc": "0.00000100",
    "lightningCount": 0,
    "lightningVolumeBtc": "0.00000000",
    "lightningFeesBtc": "0.00000000",
    "inflowBtc": "0.00010000",
    "outflowBtc": "0.00000000",
    "confirmedCount": 1,
    "pendingCount": 0,
    "failedCount": 0
  },
  "paymentLinks": {
    "linksCreated": 1,
    "linksPaid": 1,
    "linksPending": 0,
    "linksExpired": 0,
    "linksCancelled": 0,
    "totalAmountBtc": "0.00010000",
    "paidAmountBtc": "0.00010000"
  },
  "privacyBoundary": "Aggregate operational metrics only; no user timeline, invoice payload, destination, txid, or wallet name."
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/mobile

- Controller: `AdminOperationsController.mobile` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 101`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `MobileDownloadService.MobileReleaseInfo`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "version": "string",
  "buildNumber": "string",
  "artifacts": {
    "key": {
      "url": "string",
      "sha256": "string",
      "signingCertificateSha256": "string"
    }
  },
  "changelog": [
    "string"
  ],
  "generatedAt": "2026-01-01T00:00:00Z",
  "integrityInstructions": "string"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/overview

- Controller: `AdminOperationsController.overview` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 63`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `Map<String, Object>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "checkedAt": "2026-01-01T00:00:00Z",
  "health": {
    "status": "UP",
    "service": "kerosene",
    "region": "DEV",
    "timestamp": "2026-01-01T00:00:00Z",
    "checks": {
      "database": {
        "name": "database",
        "status": "UP",
        "critical": true,
        "latencyMs": 1,
        "message": "Database reachable",
        "details": {
          "source": "local"
        }
      },
      "redis": {
        "name": "database",
        "status": "UP",
        "critical": true,
        "latencyMs": 1,
        "message": "Database reachable",
        "details": {
          "source": "local"
        }
      }
    }
  },
  "blockchain": {
    "status": "UP",
    "primarySource": "BITCOIN_PRUNED_NODE_RPC",
    "network": "mainnet",
    "indexer": "http://bitcoin-indexer:3002",
    "localIndexerConfigured": true,
    "checkedAt": "2026-01-01T00:00:00Z",
    "chain": {
      "height": 840000,
      "headers": 840000,
      "bestBlockHash": "0000000000000000000000000000000000000000000000000000000000000000",
      "chain": "main",
      "difficulty": 1.0,
      "verificationProgress": 1.0,
      "initialBlockDownload": false,
      "pruned": true,
      "prunedRequired": true,
      "pruneHeight": 830000,
      "bestBlockTime": 1704067200,
      "bestBlockTxCount": 1
    },
    "mempool": {
      "transactions": 1,
      "bytes": 250,
      "usage": 1024,
      "minRelayFee": 1e-05,
      "feesSatPerVByte": {
        "fast": 12,
        "halfHour": 8,
        "hour": 5
      }
    },
    "relevantTransactions": [
      {
        "id": "00000000-0000-0000-0000-000000000000",
        "txidRef": "abcd1234...ef567890",
        "status": "SETTLED",
        "confirmations": 6,
        "network": "BITCOIN",
        "type": "ONCHAIN_WITHDRAWAL",
        "amountBtc": "0.00010000",
        "updatedAt": "2026-01-01T00:00:00"
      }
    ],
    "message": "Bitcoin pruned node is synced"
  },
  "lightning": {
    "status": "UP",
    "primarySource": "LND_GRPC",
    "checkedAt": "2026-01-01T00:00:00Z",
    "node": {
      "identityPubkey": "02abcdef",
      "alias": "kerosene-lnd",
      "version": "0.18.0",
      "syncedToChain": true,
      "syncedToGraph": true,
      "blockHeight": 840000,
      "blockHash": "0000000000000000000000000000000000000000000000000000000000000000",
      "numPeers": 1,
      "numActiveChannels": 1,
      "numInactiveChannels": 0,
      "numPendingChannels": 0,
      "localBalanceSats": 100000,
      "remoteBalanceSats": 100000,
      "walletConfirmedBalanceSats": 100000
    },
    "message": "LND is synced to chain"
  },
  "vaultRaft": {
    "status": "UP",
    "initialized": true,
    "sealed": false,
    "standby": false,
    "leaderAddress": "http://vault-raft-1:8200",
    "votingServers": 3,
    "expectedServers": 3,
    "servers": [
      {
        "nodeId": "vault-raft-1",
        "address": "vault-raft-1:8201",
        "leader": true,
        "voter": true
      }
    ],
    "checkedAt": "2026-01-01T00:00:00Z",
    "message": "Vault Raft cluster is initialized, unsealed, and has quorum",
    "details": {
      "url": "http://vault-raft-1:8200",
      "required": true,
      "serverCount": 3,
      "leaderProbe": "OK"
    }
  },
  "release": {
    "service": "kerosene-backend",
    "version": "UNCONFIGURED",
    "gitCommit": "unknown",
    "buildTime": "unknown",
    "imageDigest": "unknown",
    "codeHash": "unknown",
    "configHash": "unknown",
    "manifestDigest": "absent",
    "manifestSignatureValid": false,
    "authorized": true,
    "reason": "ATTESTATION_OPTIONAL",
    "message": "release.manifest.path is not configured",
    "runtime": {
      "service": "kerosene-backend",
      "gitCommit": "unknown",
      "buildTime": "unknown",
      "imageDigest": "unknown",
      "codeHash": "unknown",
      "configHash": "unknown",
      "profiles": "docker"
    },
    "manifestService": {
      "gitCommit": "unknown",
      "imageDigest": "unknown",
      "codeHash": "unknown",
      "configHash": "unknown"
    }
  },
  "mobile": {
    "version": "string",
    "buildNumber": "string",
    "artifacts": {
      "android": {
        "url": "string",
        "sha256": "string",
        "signingCertificateSha256": "string"
      }
    },
    "changelog": [
      "string"
    ],
    "generatedAt": "2026-01-01T00:00:00Z",
    "integrityInstructions": "string"
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/release

- Controller: `AdminOperationsController.release` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 96`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ReleaseManifestService.ReleaseSnapshot`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "service": "kerosene-backend",
  "version": "UNCONFIGURED",
  "gitCommit": "unknown",
  "buildTime": "unknown",
  "imageDigest": "unknown",
  "codeHash": "unknown",
  "configHash": "unknown",
  "manifestDigest": "absent",
  "manifestSignatureValid": false,
  "authorized": true,
  "reason": "ATTESTATION_OPTIONAL",
  "message": "release.manifest.path is not configured",
  "runtime": {
    "service": "kerosene-backend",
    "gitCommit": "unknown",
    "buildTime": "unknown",
    "imageDigest": "unknown",
    "codeHash": "unknown",
    "configHash": "unknown",
    "profiles": "docker"
  },
  "manifestService": {
    "gitCommit": "unknown",
    "imageDigest": "unknown",
    "codeHash": "unknown",
    "configHash": "unknown"
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/admin/operations/vault-raft

- Controller: `AdminOperationsController.vaultRaft` (`backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java: 91`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `VaultRaftHealthService.VaultRaftSnapshot`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "initialized": true,
  "sealed": false,
  "standby": false,
  "leaderAddress": "http://vault-raft-1:8200",
  "votingServers": 3,
  "expectedServers": 3,
  "servers": [
    {
      "nodeId": "vault-raft-1",
      "address": "vault-raft-1:8201",
      "leader": true,
      "voter": true
    }
  ],
  "checkedAt": "2026-01-01T00:00:00Z",
  "message": "Vault Raft cluster is initialized, unsealed, and has quorum",
  "details": {
    "url": "http://vault-raft-1:8200",
    "required": true,
    "serverCount": 3,
    "leaderProbe": "OK"
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/public/mobile-download

- Controller: `PublicSiteController.mobileDownload` (`backend/kerosene/src/main/java/source/common/admin/PublicSiteController.java: 17`).
- Autenticacao: `Publico`.
- Response Java: `MobileDownloadService.MobileReleaseInfo`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "version": "string",
  "buildNumber": "string",
  "artifacts": {
    "key": {
      "url": "string",
      "sha256": "string",
      "signingCertificateSha256": "string"
    }
  },
  "changelog": [
    "string"
  ],
  "generatedAt": "2026-01-01T00:00:00Z",
  "integrityInstructions": "string"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Auth and Account

### GET /auth/activation-status

- Controller: `AccountActivationController.getStatus` (`backend/kerosene/src/main/java/source/auth/controller/AccountActivationController.java: 27`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AccountActivationStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "activated": true,
    "canReceiveInbound": true,
    "requiresActivationDeposit": true,
    "requiredAmountBtc": "0.00010000",
    "paymentLinkId": "string",
    "depositAddress": "string",
    "paymentStatus": "string",
    "warningMessage": "string",
    "activatedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/activation-status/deposit-link

- Controller: `AccountActivationController.createDepositLink` (`backend/kerosene/src/main/java/source/auth/controller/AccountActivationController.java: 33`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AccountActivationStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "activated": true,
    "canReceiveInbound": true,
    "requiresActivationDeposit": true,
    "requiredAmountBtc": "0.00010000",
    "paymentLinkId": "string",
    "depositAddress": "string",
    "paymentStatus": "string",
    "warningMessage": "string",
    "activatedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/activation-status/{linkId}/confirm

- Controller: `AccountActivationController.confirm` (`backend/kerosene/src/main/java/source/auth/controller/AccountActivationController.java: 42`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AccountActivationStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `linkId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "txid": "bitcoin-txid",
  "fromAddress": "bc1qsender"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "activated": true,
    "canReceiveInbound": true,
    "requiresActivationDeposit": true,
    "requiredAmountBtc": "0.00010000",
    "paymentLinkId": "string",
    "depositAddress": "string",
    "paymentStatus": "string",
    "warningMessage": "string",
    "activatedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/admin/access-attempts/pending

- Controller: `AdminAccessController.pendingAttempts` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 93`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<List<AdminAccessAttemptDTO>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": [
    {
      "attemptId": "00000000-0000-0000-0000-000000000000",
      "status": "string",
      "deviceId": "string",
      "deviceName": "string",
      "browser": "string",
      "userAgent": "string",
      "ipFingerprint": "string",
      "requestedAt": "2026-01-01T00:00:00",
      "expiresAt": "2026-01-01T00:00:00"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/admin/access-attempts/{attemptId}/decision

- Controller: `AdminAccessController.decide` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 101`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<AdminAccessAttemptDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `attemptId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "decision": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "attemptId": "00000000-0000-0000-0000-000000000000",
    "status": "string",
    "deviceId": "string",
    "deviceName": "string",
    "browser": "string",
    "userAgent": "string",
    "ipFingerprint": "string",
    "requestedAt": "2026-01-01T00:00:00",
    "expiresAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/admin/devices

- Controller: `AdminAccessController.devices` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 113`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<List<AdminAuthenticatedDeviceDTO>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": [
    {
      "deviceId": "string",
      "deviceName": "string",
      "browser": "string",
      "userAgent": "string",
      "status": "string",
      "firstAccessAt": "2026-01-01T00:00:00",
      "lastAccessAt": "2026-01-01T00:00:00"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/admin/devices/{deviceId}/block

- Controller: `AdminAccessController.blockDevice` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 121`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<AdminAuthenticatedDeviceDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `deviceId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "deviceId": "string",
    "deviceName": "string",
    "browser": "string",
    "userAgent": "string",
    "status": "string",
    "firstAccessAt": "2026-01-01T00:00:00",
    "lastAccessAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/admin/devices/{deviceId}/revoke

- Controller: `AdminAccessController.revokeDevice` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 132`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<AdminAuthenticatedDeviceDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `deviceId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "deviceId": "string",
    "deviceName": "string",
    "browser": "string",
    "userAgent": "string",
    "status": "string",
    "firstAccessAt": "2026-01-01T00:00:00",
    "lastAccessAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### DELETE /auth/admin/key

- Controller: `AdminAccessController.revokeKey` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 85`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<AdminKeyStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "configured": true,
    "status": "string",
    "fingerprint": "string",
    "createdAt": "2026-01-01T00:00:00",
    "revokedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/admin/key

- Controller: `AdminAccessController.keyStatus` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 77`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<AdminKeyStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "configured": true,
    "status": "string",
    "fingerprint": "string",
    "createdAt": "2026-01-01T00:00:00",
    "revokedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/admin/key

- Controller: `AdminAccessController.createOrRotateKey` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 69`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<AdminKeyStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "keyMaterialHash": "string",
  "deviceInstallId": "00000000-0000-0000-0000-000000000000"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "configured": true,
    "status": "string",
    "fingerprint": "string",
    "createdAt": "2026-01-01T00:00:00",
    "revokedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/admin/login

- Controller: `AdminAccessController.startLogin` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 40`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<AdminLoginResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "username": "alice",
  "password": [
    "value"
  ],
  "adminKeyProof": "string",
  "deviceId": "00000000-0000-0000-0000-000000000000",
  "deviceName": "string",
  "browser": "string",
  "userAgent": "string",
  "platform": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "status": "string",
    "requiresMobileApproval": true,
    "attemptId": "00000000-0000-0000-0000-000000000000",
    "expiresAt": "2026-01-01T00:00:00",
    "token": "string",
    "message": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/admin/login/{attemptId}

- Controller: `AdminAccessController.pollLogin` (`backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java: 57`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AdminLoginResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `attemptId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "status": "string",
    "requiresMobileApproval": true,
    "attemptId": "00000000-0000-0000-0000-000000000000",
    "expiresAt": "2026-01-01T00:00:00",
    "token": "string",
    "message": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/backup-codes

- Controller: `BackupCodesController.getStatus` (`backend/kerosene/src/main/java/source/auth/controller/BackupCodesController.java: 23`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<BackupCodesStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "enabled": true,
    "remainingCodes": 1,
    "newlyGeneratedCodes": [
      "string"
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/backup-codes/regenerate

- Controller: `BackupCodesController.regenerate` (`backend/kerosene/src/main/java/source/auth/controller/BackupCodesController.java: 29`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<BackupCodesStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "enabled": true,
    "remainingCodes": 1,
    "newlyGeneratedCodes": [
      "string"
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/login

- Controller: `UserController.login` (`backend/kerosene/src/main/java/source/auth/controller/UserController.java: 40`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "username": "alice",
  "password": "correct-horse-battery-staple",
  "challenge": "pow-challenge",
  "nonce": "pow-nonce"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/login/totp/verify

- Controller: `UserController.loginTotpVerify` (`backend/kerosene/src/main/java/source/auth/controller/UserController.java: 62`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "preAuthToken": "pre-auth-token",
  "totpCode": "123456"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/me

- Controller: `MeController.getCurrentUser` (`backend/kerosene/src/main/java/source/auth/controller/MeController.java: 31`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `X-Device-Hash` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "1",
    "userId": "1",
    "username": "alice",
    "role": "USER",
    "isAdmin": false,
    "testBalanceClaimed": true,
    "passkeyEnabledForTransactions": true,
    "appPinEnabled": true,
    "createdAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/passkey/challenge

- Controller: `PasskeyController.getChallenge` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 68`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `username` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/passkey/devices

- Controller: `PasskeyController.getRegisteredDevices` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 74`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PasskeyInventoryDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "passkeyRegistered": true,
    "compatibleForCurrentLogin": true,
    "legacyCredentialsPresent": true,
    "currentRelyingPartyId": "string",
    "currentHost": "string",
    "devices": [
      {
        "credentialRef": "string",
        "deviceName": "string",
        "brand": "string",
        "model": "string",
        "serialNumber": "string",
        "deviceInstallId": "string",
        "platform": "string",
        "browser": "string",
        "firstAccessAt": "2026-01-01T00:00:00",
        "lastAccessAt": "2026-01-01T00:00:00",
        "status": "string",
        "relyingPartyId": "string",
        "originHost": "string",
        "compatibilityStatus": "string",
        "compatibleWithCurrentLogin": true
      }
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/passkey/devices/{deviceInstallId}/block

- Controller: `PasskeyController.blockDevice` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 183`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PasskeyInventoryDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `deviceInstallId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "passkeyRegistered": true,
    "compatibleForCurrentLogin": true,
    "legacyCredentialsPresent": true,
    "currentRelyingPartyId": "string",
    "currentHost": "string",
    "devices": [
      {
        "credentialRef": "string",
        "deviceName": "string",
        "brand": "string",
        "model": "string",
        "serialNumber": "string",
        "deviceInstallId": "string",
        "platform": "string",
        "browser": "string",
        "firstAccessAt": "2026-01-01T00:00:00",
        "lastAccessAt": "2026-01-01T00:00:00",
        "status": "string",
        "relyingPartyId": "string",
        "originHost": "string",
        "compatibilityStatus": "string",
        "compatibleWithCurrentLogin": true
      }
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/passkey/devices/{deviceInstallId}/revoke

- Controller: `PasskeyController.revokeDevice` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 188`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PasskeyInventoryDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `deviceInstallId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "passkeyRegistered": true,
    "compatibleForCurrentLogin": true,
    "legacyCredentialsPresent": true,
    "currentRelyingPartyId": "string",
    "currentHost": "string",
    "devices": [
      {
        "credentialRef": "string",
        "deviceName": "string",
        "brand": "string",
        "model": "string",
        "serialNumber": "string",
        "deviceInstallId": "string",
        "platform": "string",
        "browser": "string",
        "firstAccessAt": "2026-01-01T00:00:00",
        "lastAccessAt": "2026-01-01T00:00:00",
        "status": "string",
        "relyingPartyId": "string",
        "originHost": "string",
        "compatibilityStatus": "string",
        "compatibleWithCurrentLogin": true
      }
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/passkey/onboarding/finish

- Controller: `PasskeyController.finishOnboardingRegistration` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 363`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `sessionId` | `String` | true | `` |

Request body:

```json
{
  "publicKey": "string",
  "deviceName": "string",
  "signature": "string",
  "authData": "string",
  "clientDataJSON": "string",
  "credentialId": "00000000-0000-0000-0000-000000000000",
  "userHandle": "string",
  "publicKeyCose": "string",
  "brand": "string",
  "model": "string",
  "serialNumber": "string",
  "deviceInstallId": "00000000-0000-0000-0000-000000000000",
  "platform": "string",
  "browser": "string",
  "status": "ACTIVE"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/passkey/onboarding/start

- Controller: `PasskeyController.startOnboardingRegistration` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 351`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `sessionId` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/passkey/register

- Controller: `PasskeyController.registerPasskey` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 96`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "publicKey": "string",
  "deviceName": "string",
  "signature": "string",
  "authData": "string",
  "clientDataJSON": "string",
  "credentialId": "00000000-0000-0000-0000-000000000000",
  "userHandle": "string",
  "publicKeyCose": "string",
  "brand": "string",
  "model": "string",
  "serialNumber": "string",
  "deviceInstallId": "00000000-0000-0000-0000-000000000000",
  "platform": "string",
  "browser": "string",
  "status": "ACTIVE"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/passkey/verify

- Controller: `PasskeyController.verifyAndLogin` (`backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java: 196`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<Object>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "username": "alice",
  "signature": "string",
  "authData": "string",
  "clientDataJSON": "string",
  "credentialId": "00000000-0000-0000-0000-000000000000"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "jwt-token",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/pow/challenge

- Controller: `UserController.getPowChallenge` (`backend/kerosene/src/main/java/source/auth/controller/UserController.java: 34`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, String>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "challenge": "pow-challenge-token"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/recovery/emergency/finish

- Controller: `EmergencyRecoveryController.finish` (`backend/kerosene/src/main/java/source/auth/controller/EmergencyRecoveryController.java: 52`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<EmergencyRecoveryFinishResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "recoverySessionId": "00000000-0000-0000-0000-000000000000",
  "totpCode": "123456",
  "publicKey": "string",
  "publicKeyCose": "string",
  "deviceName": "string",
  "signature": "string",
  "authData": "string",
  "clientDataJSON": "string",
  "credentialId": "00000000-0000-0000-0000-000000000000",
  "userHandle": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "username": "string",
    "newBackupCodes": [
      "string"
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/recovery/emergency/start

- Controller: `EmergencyRecoveryController.start` (`backend/kerosene/src/main/java/source/auth/controller/EmergencyRecoveryController.java: 28`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<EmergencyRecoveryStartResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "username": "alice",
  "newPassphrase": "new-passphrase",
  "recoveryCodes": [
    "code-1",
    "code-2"
  ],
  "challenge": "pow-challenge",
  "nonce": "pow-nonce"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "recoverySessionId": "string",
    "otpUri": "string",
    "passkeyChallenge": "string",
    "expiresInSeconds": 1,
    "requiredRecoveryCodes": 1
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/security-status

- Controller: `AccountSecurityStatusController.getStatus` (`backend/kerosene/src/main/java/source/auth/controller/AccountSecurityStatusController.java: 22`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AccountSecurityStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "passwordConfigured": true,
    "passkeyRegistered": true,
    "totpEnabled": true,
    "backupCodesRemaining": 1,
    "unprotected": true,
    "warningMessage": "string",
    "accountActivated": true,
    "inboundEnabled": true,
    "passkeys": {
      "passkeyRegistered": true,
      "compatibleForCurrentLogin": true,
      "legacyCredentialsPresent": true,
      "currentRelyingPartyId": "string",
      "currentHost": "string",
      "devices": [
        {
          "credentialRef": "string",
          "deviceName": "string",
          "brand": "string",
          "model": "string",
          "serialNumber": "string",
          "deviceInstallId": "string",
          "platform": "string",
          "browser": "string",
          "firstAccessAt": "2026-01-01T00:00:00",
          "lastAccessAt": "2026-01-01T00:00:00",
          "status": "string",
          "relyingPartyId": "string",
          "originHost": "string",
          "compatibilityStatus": "string",
          "compatibleWithCurrentLogin": true
        }
      ]
    }
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/security/app-pin

- Controller: `AppPinController.getStatus` (`backend/kerosene/src/main/java/source/auth/controller/AppPinController.java: 28`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AppPinStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `X-Device-Hash` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "enabled": true,
    "configured": true,
    "locked": true,
    "failedAttempts": 1,
    "remainingAttempts": 1,
    "maxAttempts": 1,
    "minPinLength": 1,
    "maxPinLength": 1,
    "resettableWithTotp": true,
    "deviceScoped": true,
    "lockedUntil": "2026-01-01T00:00:00",
    "lastVerifiedAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### PUT /auth/security/app-pin

- Controller: `AppPinController.configure` (`backend/kerosene/src/main/java/source/auth/controller/AppPinController.java: 36`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AppPinStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `X-Device-Hash` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "enabled": true,
  "pin": "string",
  "currentPin": "string",
  "totpCode": "123456"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "enabled": true,
    "configured": true,
    "locked": true,
    "failedAttempts": 1,
    "remainingAttempts": 1,
    "maxAttempts": 1,
    "minPinLength": 1,
    "maxPinLength": 1,
    "resettableWithTotp": true,
    "deviceScoped": true,
    "lockedUntil": "2026-01-01T00:00:00",
    "lastVerifiedAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/security/app-pin/verify

- Controller: `AppPinController.verify` (`backend/kerosene/src/main/java/source/auth/controller/AppPinController.java: 45`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AppPinStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `X-Device-Hash` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "pin": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "enabled": true,
    "configured": true,
    "locked": true,
    "failedAttempts": 1,
    "remainingAttempts": 1,
    "maxAttempts": 1,
    "minPinLength": 1,
    "maxPinLength": 1,
    "resettableWithTotp": true,
    "deviceScoped": true,
    "lockedUntil": "2026-01-01T00:00:00",
    "lastVerifiedAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /auth/security/profile

- Controller: `AccountSecurityController.getProfile` (`backend/kerosene/src/main/java/source/auth/controller/AccountSecurityController.java: 46`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AccountSecurityProfileDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `X-Device-Hash` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "accountSecurity": "ACTIVE",
    "shamirTotalShares": 1,
    "shamirThreshold": 1,
    "multisigThreshold": 1,
    "passkeyAvailable": true,
    "passkeyEnabledForTransactions": true,
    "appPin": {
      "enabled": true,
      "configured": true,
      "locked": true,
      "failedAttempts": 1,
      "remainingAttempts": 1,
      "maxAttempts": 1,
      "minPinLength": 1,
      "maxPinLength": 1,
      "resettableWithTotp": true,
      "deviceScoped": true,
      "lockedUntil": "2026-01-01T00:00:00",
      "lastVerifiedAt": "2026-01-01T00:00:00",
      "updatedAt": "2026-01-01T00:00:00"
    },
    "requiredFactors": [
      "string"
    ],
    "passkeys": {
      "passkeyRegistered": true,
      "compatibleForCurrentLogin": true,
      "legacyCredentialsPresent": true,
      "currentRelyingPartyId": "string",
      "currentHost": "string",
      "devices": [
        {
          "credentialRef": "string",
          "deviceName": "string",
          "brand": "string",
          "model": "string",
          "serialNumber": "string",
          "deviceInstallId": "string",
          "platform": "string",
          "browser": "string",
          "firstAccessAt": "2026-01-01T00:00:00",
          "lastAccessAt": "2026-01-01T00:00:00",
          "status": "string",
          "relyingPartyId": "string",
          "originHost": "string",
          "compatibilityStatus": "string",
          "compatibleWithCurrentLogin": true
        }
      ]
    }
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### PUT /auth/security/profile

- Controller: `AccountSecurityController.updateProfile` (`backend/kerosene/src/main/java/source/auth/controller/AccountSecurityController.java: 61`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<AccountSecurityProfileDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `X-Device-Hash` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "accountSecurity": "STANDARD",
  "shamirTotalShares": 1,
  "shamirThreshold": 1,
  "multisigThreshold": 1
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "accountSecurity": "ACTIVE",
    "shamirTotalShares": 1,
    "shamirThreshold": 1,
    "multisigThreshold": 1,
    "passkeyAvailable": true,
    "passkeyEnabledForTransactions": true,
    "appPin": {
      "enabled": true,
      "configured": true,
      "locked": true,
      "failedAttempts": 1,
      "remainingAttempts": 1,
      "maxAttempts": 1,
      "minPinLength": 1,
      "maxPinLength": 1,
      "resettableWithTotp": true,
      "deviceScoped": true,
      "lockedUntil": "2026-01-01T00:00:00",
      "lastVerifiedAt": "2026-01-01T00:00:00",
      "updatedAt": "2026-01-01T00:00:00"
    },
    "requiredFactors": [
      "string"
    ],
    "passkeys": {
      "passkeyRegistered": true,
      "compatibleForCurrentLogin": true,
      "legacyCredentialsPresent": true,
      "currentRelyingPartyId": "string",
      "currentHost": "string",
      "devices": [
        {
          "credentialRef": "string",
          "deviceName": "string",
          "brand": "string",
          "model": "string",
          "serialNumber": "string",
          "deviceInstallId": "string",
          "platform": "string",
          "browser": "string",
          "firstAccessAt": "2026-01-01T00:00:00",
          "lastAccessAt": "2026-01-01T00:00:00",
          "status": "string",
          "relyingPartyId": "string",
          "originHost": "string",
          "compatibilityStatus": "string",
          "compatibleWithCurrentLogin": true
        }
      ]
    }
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/signup

- Controller: `UserController.signup` (`backend/kerosene/src/main/java/source/auth/controller/UserController.java: 47`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<SignupResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "username": "alice",
  "password": "correct-horse-battery-staple",
  "challenge": "pow-challenge",
  "nonce": "pow-nonce",
  "accountSecurity": "STANDARD"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "sessionId": "string",
    "otpUri": "string",
    "backupCodes": [
      "string"
    ],
    "totpOptional": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/signup/totp/verify

- Controller: `UserController.totpCodeVerify` (`backend/kerosene/src/main/java/source/auth/controller/UserController.java: 54`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "sessionId": "signup-session-id",
  "totpCode": "123456"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### DELETE /auth/totp

- Controller: `TotpController.disable` (`backend/kerosene/src/main/java/source/auth/controller/TotpController.java: 44`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "string",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/totp/setup

- Controller: `TotpController.setup` (`backend/kerosene/src/main/java/source/auth/controller/TotpController.java: 27`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<TotpSetupResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "otpUri": "string",
    "secret": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /auth/totp/verify

- Controller: `TotpController.verify` (`backend/kerosene/src/main/java/source/auth/controller/TotpController.java: 34`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<BackupCodesStatusDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "totpCode": "123456"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "enabled": true,
    "remainingCodes": 1,
    "newlyGeneratedCodes": [
      "string"
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Public Web and Health

### GET /bitcoin-banking

- Controller: `WebAdminController.webRoutes` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 15`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin-banking/**

- Controller: `WebAdminController.webRoutes` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 15`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Public Web and Health

### GET /download

- Controller: `WebAdminController.webRoutes` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 15`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /health/dependencies

- Controller: `HealthController.dependencies` (`backend/kerosene/src/main/java/source/common/controller/HealthController.java: 29`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<OperationalHealthSnapshot>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "service": "kerosene",
  "region": "DEV",
  "timestamp": "2026-01-01T00:00:00Z",
  "checks": {
    "database": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    },
    "redis": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    }
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /health/live

- Controller: `HealthController.live` (`backend/kerosene/src/main/java/source/common/controller/HealthController.java: 19`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<OperationalHealthSnapshot>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "service": "kerosene",
  "region": "DEV",
  "timestamp": "2026-01-01T00:00:00Z",
  "checks": {
    "database": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    },
    "redis": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    }
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /health/ready

- Controller: `HealthController.ready` (`backend/kerosene/src/main/java/source/common/controller/HealthController.java: 24`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<OperationalHealthSnapshot>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "UP",
  "service": "kerosene",
  "region": "DEV",
  "timestamp": "2026-01-01T00:00:00Z",
  "checks": {
    "database": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    },
    "redis": {
      "name": "database",
      "status": "UP",
      "critical": true,
      "latencyMs": 1,
      "message": "Database reachable",
      "details": {
        "source": "local"
      }
    }
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /healthz

- Controller: `RootStatusController.healthz` (`backend/kerosene/src/main/java/source/common/controller/RootStatusController.java: 26`).
- Autenticacao: `Publico`.
- Response Java: `Map<String, Object>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "status": "ok",
  "service": "kerosene",
  "region": "DEV",
  "timestamp": "2026-01-01T00:00:00Z",
  "health": "/health/ready",
  "liveness": "/health/live",
  "sovereignty": "/sovereignty/status"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## KFE

Verificacao de saude em `2026-06-12`:

| Check | Resultado | Evidencia |
| --- | --- | --- |
| Compilacao e testes KFE | Saudavel | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests 'source.kfe.*'` concluiu com `BUILD SUCCESSFUL`; o comando executou `compileJava`, `processResources` e a suite filtrada de KFE. |
| `processResources` | Saudavel | A tarefa executou no caminho normal do teste; o workaround de Gradle para artefatos antigos de `build/resources/main` evita o erro anterior em `Icon-512.png`. |
| Health runtime local | Nao verificado nesta referencia | Esta secao valida mapeamento/compilacao/testes KFE. A saude runtime deve ser verificada contra um backend em execucao via `GET /health/live`, `GET /health/ready` e `GET /healthz`. |
| JDK de verificacao | Java 21 | O build Gradle Kotlin DSL foi executado com `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`, alinhado ao `sourceCompatibility` do backend. |

Status consolidado: as rotas KFE abaixo estao mapeadas nos controllers atuais e a suite KFE passa com Java 21. A disponibilidade runtime depende de uma instancia backend em execucao e deve ser acompanhada pelos endpoints publicos de health.

### GET /api/admin/kfe/audit/latest

- Controller: `KfeAuditAdminController.latest` (`backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java:31`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<KfeAuditLatestResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE audit latest root retrieved.",
  "data": "KfeAuditLatestResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` requer `ROLE_ADMIN`, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### GET /api/admin/kfe/audit/events

- Controller: `KfeAuditAdminController.events` (`backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java:36`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<List<KfeAuditEventResponse>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `limit` | `int` | no | `50` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE audit events retrieved.",
  "data": [
    "KfeAuditEventResponse"
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` requer `ROLE_ADMIN`, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### GET /api/admin/kfe/audit/transactions/{transactionId}

- Controller: `KfeAuditAdminController.transactionEvents` (`backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java:42`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<List<KfeAuditEventResponse>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `transactionId` | `UUID` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE transaction audit events retrieved.",
  "data": [
    "KfeAuditEventResponse"
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` requer `ROLE_ADMIN`, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### POST /api/admin/kfe/audit/root

- Controller: `KfeAuditAdminController.root` (`backend/kerosene/src/main/java/source/kfe/controller/KfeAuditAdminController.java:50`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<ApiResponse<KfeAuditRootResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE audit root computed.",
  "data": "KfeAuditRootResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` requer `ROLE_ADMIN`, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### GET /kfe/dashboard

- Controller: `KfeDashboardController.dashboard` (`backend/kerosene/src/main/java/source/kfe/controller/KfeDashboardController.java:22`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeDashboardResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE dashboard retrieved.",
  "data": "KfeDashboardResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### GET /kfe/users/{receiverIdentifier}/receiving-capabilities

- Controller: `KfeReceivingController.capabilities` (`backend/kerosene/src/main/java/source/kfe/controller/KfeReceivingController.java:22`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeReceivingCapabilitiesResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `receiverIdentifier` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE receiving capabilities retrieved.",
  "data": "KfeReceivingCapabilitiesResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### POST /kfe/transactions

- Controller: `KfeTransactionController.submit` (`backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java:38`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeTransactionResponse>>`.
- Idempotencia: em conflito de integridade, o controller recalcula o hash do request e retorna a transacao existente para a mesma chave de idempotencia quando compativel.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Objeto JSON validado por `KfeSubmitTransactionRequest`.

Response body:

```json
{
  "success": true,
  "message": "KFE transaction accepted.",
  "data": "KfeTransactionResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de idempotencia/estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado.

### GET /kfe/transactions/{transactionId}

- Controller: `KfeTransactionController.get` (`backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java:53`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeTransactionResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `transactionId` | `UUID` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE transaction retrieved.",
  "data": "KfeTransactionResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` transacao KFE inexistente para o usuario autenticado, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### POST /kfe/wallets

- Controller: `KfeWalletController.create` (`backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java:40`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeWalletResponse>>`.
- Status de sucesso: `201 Created`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Objeto JSON validado por `KfeCreateWalletRequest`.

Response body:

```json
{
  "success": true,
  "message": "KFE wallet created.",
  "data": "KfeWalletResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado.

### GET /kfe/wallets

- Controller: `KfeWalletController.list` (`backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java:49`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<KfeWalletResponse>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE wallets retrieved.",
  "data": [
    "KfeWalletResponse"
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### POST /kfe/wallets/{walletId}/addresses/rotate

- Controller: `KfeWalletController.rotateAddress` (`backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java:56`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeAddressResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `walletId` | `UUID` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE wallet address rotated.",
  "data": "KfeAddressResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` carteira KFE inexistente para o usuario autenticado, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### GET /kfe/wallets/{walletId}/utxos

- Controller: `KfeWalletController.listUtxos` (`backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java:65`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<KfeUtxoResponse>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `walletId` | `UUID` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "KFE wallet UTXOs retrieved.",
  "data": [
    "KfeUtxoResponse"
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` carteira KFE inexistente para o usuario autenticado, `409` conflito de estado, `429` rate limit, `500` erro nao tratado.

### POST /kfe/wallets/{walletId}/cold-wallet/psbt

- Controller: `KfeWalletController.createColdWalletPsbt` (`backend/kerosene/src/main/java/source/kfe/controller/KfeWalletController.java:74`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<KfeColdWalletPsbtResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `walletId` | `UUID` | yes |

Query params:

Nenhum.

Request body:

Objeto JSON validado por `KfeColdWalletPsbtRequest`.

Response body:

```json
{
  "success": true,
  "message": "KFE cold wallet PSBT created.",
  "data": "KfeColdWalletPsbtResponse",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido, `403` autorizacao insuficiente, `404` carteira KFE inexistente para o usuario autenticado, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado.

## Mining

### GET /mining/allocations

- Controller: `MiningController.listAllocations` (`backend/kerosene/src/main/java/source/mining/controller/MiningController.java: 47`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<MiningAllocationResponseDTO>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "rigId": 1,
      "rigName": "string",
      "walletName": "string",
      "algorithm": "string",
      "allocatedHashrate": "0.00010000",
      "hashUnit": "string",
      "durationHours": 1,
      "rentalCostBtc": "0.00010000",
      "projectedGrossYieldBtc": "0.00010000",
      "projectedNetYieldBtc": "0.00010000",
      "refundedAmountBtc": "0.00010000",
      "status": "string",
      "providerRentalReference": "string",
      "payoutAddress": "string",
      "poolUrl": "string",
      "workerName": "string",
      "startsAt": "2026-01-01T00:00:00",
      "endsAt": "2026-01-01T00:00:00",
      "settledAt": "2026-01-01T00:00:00"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /mining/allocations

- Controller: `MiningController.createAllocation` (`backend/kerosene/src/main/java/source/mining/controller/MiningController.java: 37`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<MiningAllocationResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "walletName": "main",
  "rigId": "00000000-0000-0000-0000-000000000000",
  "requestedHashrate": "0.00010000",
  "budgetBtc": "0.00010000",
  "durationHours": 1,
  "payoutAddress": "bc1qexampleaddress",
  "poolUrl": "string",
  "workerName": "string",
  "totpCode": "123456",
  "passkeyAssertionResponseJSON": "string",
  "confirmationPassphrase": "correct-horse-battery-staple"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "rigId": 1,
    "rigName": "string",
    "walletName": "string",
    "algorithm": "string",
    "allocatedHashrate": "0.00010000",
    "hashUnit": "string",
    "durationHours": 1,
    "rentalCostBtc": "0.00010000",
    "projectedGrossYieldBtc": "0.00010000",
    "projectedNetYieldBtc": "0.00010000",
    "refundedAmountBtc": "0.00010000",
    "status": "string",
    "providerRentalReference": "string",
    "payoutAddress": "string",
    "poolUrl": "string",
    "workerName": "string",
    "startsAt": "2026-01-01T00:00:00",
    "endsAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /mining/allocations/{allocationId}

- Controller: `MiningController.getAllocation` (`backend/kerosene/src/main/java/source/mining/controller/MiningController.java: 54`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<MiningAllocationResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `allocationId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "rigId": 1,
    "rigName": "string",
    "walletName": "string",
    "algorithm": "string",
    "allocatedHashrate": "0.00010000",
    "hashUnit": "string",
    "durationHours": 1,
    "rentalCostBtc": "0.00010000",
    "projectedGrossYieldBtc": "0.00010000",
    "projectedNetYieldBtc": "0.00010000",
    "refundedAmountBtc": "0.00010000",
    "status": "string",
    "providerRentalReference": "string",
    "payoutAddress": "string",
    "poolUrl": "string",
    "workerName": "string",
    "startsAt": "2026-01-01T00:00:00",
    "endsAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /mining/allocations/{allocationId}/cancel

- Controller: `MiningController.cancelAllocation` (`backend/kerosene/src/main/java/source/mining/controller/MiningController.java: 63`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<MiningAllocationResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `allocationId` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "rigId": 1,
    "rigName": "string",
    "walletName": "string",
    "algorithm": "string",
    "allocatedHashrate": "0.00010000",
    "hashUnit": "string",
    "durationHours": 1,
    "rentalCostBtc": "0.00010000",
    "projectedGrossYieldBtc": "0.00010000",
    "projectedNetYieldBtc": "0.00010000",
    "refundedAmountBtc": "0.00010000",
    "status": "string",
    "providerRentalReference": "string",
    "payoutAddress": "string",
    "poolUrl": "string",
    "workerName": "string",
    "startsAt": "2026-01-01T00:00:00",
    "endsAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /mining/rigs

- Controller: `MiningController.listRigOffers` (`backend/kerosene/src/main/java/source/mining/controller/MiningController.java: 31`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<MiningRigOfferDTO>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": [
    {
      "id": 1,
      "rigCode": "string",
      "displayName": "string",
      "algorithm": "string",
      "hashUnit": "string",
      "availableHashrate": "0.00010000",
      "pricePerUnitDayBtc": "0.00010000",
      "projectedBtcYieldPerUnitDay": "0.00010000",
      "minRentalHours": 1,
      "maxRentalHours": 1,
      "provider": "string"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Notifications

### GET /notifications

- Controller: `NotificationController.getNotifications` (`backend/kerosene/src/main/java/source/notification/controller/NotificationController.java: 34`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<List<NotificationEntity>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
[
  "string"
]
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /notifications/device-tokens

- Controller: `NotificationController.activeDeviceTokens` (`backend/kerosene/src/main/java/source/notification/controller/NotificationController.java: 52`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<List<DeviceTokenResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
[
  {
    "id": 1,
    "platform": "string",
    "tokenRef": "string",
    "deviceRef": "string",
    "appVersion": "string",
    "createdAt": "2026-01-01T00:00:00",
    "lastSeenAt": "2026-01-01T00:00:00",
    "revokedAt": "2026-01-01T00:00:00",
    "active": true
  }
]
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### DELETE /notifications/device-tokens/{id}

- Controller: `NotificationController.revokeToken` (`backend/kerosene/src/main/java/source/notification/controller/NotificationController.java: 59`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<Void>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `id` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

Sem corpo.

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /notifications/register-token

- Controller: `NotificationController.registerToken` (`backend/kerosene/src/main/java/source/notification/controller/NotificationController.java: 45`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<DeviceTokenResponse>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "platform": "string",
  "token": "token",
  "deviceId": "00000000-0000-0000-0000-000000000000",
  "appVersion": "string"
}
```

Response body:

```json
{
  "id": 1,
  "platform": "string",
  "tokenRef": "string",
  "deviceRef": "string",
  "appVersion": "string",
  "createdAt": "2026-01-01T00:00:00",
  "lastSeenAt": "2026-01-01T00:00:00",
  "revokedAt": "2026-01-01T00:00:00",
  "active": true
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### PUT /notifications/{id}/read

- Controller: `NotificationController.markAsRead` (`backend/kerosene/src/main/java/source/notification/controller/NotificationController.java: 39`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<Void>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `id` | `string` | yes |

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

Sem corpo.

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Sovereignty

### GET /sovereignty/ping

- Controller: `SovereigntyStatusController.ping` (`backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java: 199`).
- Autenticacao: `Publico`.
- Response Java: `String`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /sovereignty/reattest

- Controller: `SovereigntyStatusController.reAttestNode` (`backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java: 142`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<Map<String, String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `X-Admin-Token` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "message": "Node re-attested successfully. STALL mode will clear on next polling cycle."
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /sovereignty/status

- Controller: `SovereigntyStatusController.getSovereigntyStatus` (`backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java: 55`).
- Autenticacao: `Publico`.
- Response Java: `Map<String, Object>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "hardwareAttestation": {
    "status": "VERIFIED",
    "chip": "TPM 2.0",
    "lastValidatedSecondsAgo": 1,
    "totalChecks": 1,
    "quoteHash": "abcd1234...ef567890",
    "tmeEnabled": true,
    "coldBootRisk": "MITIGATED"
  },
  "networkConsensus": {
    "status": "ACTIVE",
    "activeNodes": 3,
    "failStopMode": false,
    "transactionsAccepted": 1,
    "requiredNodes": 2,
    "totalNodes": 3,
    "remotePeers": 2,
    "consensusAlgorithm": "Raft-2PC"
  },
  "ledgerIntegrity": {
    "status": "VALID",
    "lastRootHash": "abcd1234...ef567890",
    "computedAt": "2026-01-01T00:00:00",
    "ledgerCount": 1
  },
  "memoryProtection": {
    "status": "LOCKED",
    "mechanism": "mlock() via JVM native",
    "shardLocation": "tmpfs (volatile RAM)",
    "diskPersistence": false
  },
  "serverUptimeSeconds": 1,
  "serverTimestamp": "2026-01-01T00:00:00Z"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /sovereignty/telemetry

- Controller: `SovereigntyStatusController.getTelemetry` (`backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java: 173`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<Map<String, Object>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `X-Admin-Token` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "snapshotAt": "2026-01-01T00:00:00Z",
  "storage": "RAM_ONLY - no disk persistence",
  "counters": {
    "quorumFailures": 0,
    "stallEvents": 0,
    "heartbeatFailures": 0,
    "tpmChecksTotal": 1,
    "tpmMismatches": 0,
    "suicideTriggers": 0,
    "transactionsProposed": 0,
    "transactionsAccepted": 0
  },
  "recentEvents": [
    "[2026-01-01T00:00:00Z] #1 TPM_CHECK"
  ]
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Public Web and Health

### GET /status

- Controller: `WebAdminController.webRoutes` (`backend/kerosene/src/main/java/source/common/controller/WebAdminController.java: 15`).
- Autenticacao: `Publico`.
- Response Java: `text/html forward to /index.html`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `text/html` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```html
<html>...</html>
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /system/release

- Controller: `SystemReleaseController.release` (`backend/kerosene/src/main/java/source/common/admin/SystemReleaseController.java: 16`).
- Autenticacao: `Publico`.
- Response Java: `ReleaseManifestService.ReleaseSnapshot`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
{
  "service": "kerosene-backend",
  "version": "UNCONFIGURED",
  "gitCommit": "unknown",
  "buildTime": "unknown",
  "imageDigest": "unknown",
  "codeHash": "unknown",
  "configHash": "unknown",
  "manifestDigest": "absent",
  "manifestSignatureValid": false,
  "authorized": true,
  "reason": "ATTESTATION_OPTIONAL",
  "message": "release.manifest.path is not configured",
  "runtime": {
    "service": "kerosene-backend",
    "gitCommit": "unknown",
    "buildTime": "unknown",
    "imageDigest": "unknown",
    "codeHash": "unknown",
    "configHash": "unknown",
    "profiles": "docker"
  },
  "manifestService": {
    "gitCommit": "unknown",
    "imageDigest": "unknown",
    "codeHash": "unknown",
    "configHash": "unknown"
  }
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## KFE Reserve Overview

### GET /api/admin/kfe/reserves/overview

- Controller: `KfeReserveAdminController.overview`.
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ApiResponse<KfeReserveOverviewResponse>`.
- Status: ativo. Substitui semanticamente o antigo `REMOVED_LEGACY_FINANCIAL_ROUTE` sem restaurar `source.treasury`.

Response body:

```json
{
  "success": true,
  "message": "KFE reserve overview retrieved.",
  "data": {
    "totalOnchainBtc": 0.0,
    "lightningNodeBtc": 0.0,
    "inboundLiquidityBtc": 0.0,
    "outboundLiquidityBtc": 0.0,
    "reservedOnchainBtc": 0.0,
    "reservedLightningBtc": 0.0,
    "availableOnchainBtc": 0.0,
    "availableLightningBtc": 0.0,
    "lightningSendsAllowed": true,
    "liquidityState": "HEALTHY"
  }
}
```


## WebSocket/STOMP

| Endpoint | Uso | Auth | Subscriptions principais |
| --- | --- | --- | --- |
| `/ws/balance` | SockJS/STOMP para app autenticado | Header STOMP `Authorization: Bearer <jwt>` no `CONNECT` | `/user/queue/balance`, `/user/queue/notifications` |
| `/ws/raw-balance` | WebSocket raw alternativo | configurado sem SockJS | uso interno/diagnostico |
| `/ws/payment-request` | Eventos de payment request | Header STOMP `Authorization: Bearer <jwt>` no `CONNECT` | filas de usuario/autorizadas pelo interceptor |
| `/ws/raw-payment-request` | Variante raw | configurado sem SockJS | uso interno/diagnostico |

A autenticacao HTTP de `/ws/**` e liberada para permitir upgrade; a validacao real acontece no interceptor STOMP de `CONNECT`.

## DTOs E Schemas Principais

### AccountActivationStatusDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AccountActivationStatusDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `activated` | `boolean` | `-` |
| `canReceiveInbound` | `boolean` | `-` |
| `requiresActivationDeposit` | `boolean` | `-` |
| `requiredAmountBtc` | `BigDecimal` | `-` |
| `paymentLinkId` | `String` | `-` |
| `depositAddress` | `String` | `-` |
| `paymentStatus` | `String` | `-` |
| `warningMessage` | `String` | `-` |
| `activatedAt` | `LocalDateTime` | `-` |

### AccountSecurityProfileDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AccountSecurityProfileDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `accountSecurity` | `AccountSecurityType` | `-` |
| `shamirTotalShares` | `Integer` | `-` |
| `shamirThreshold` | `Integer` | `-` |
| `multisigThreshold` | `Integer` | `-` |
| `passkeyAvailable` | `boolean` | `-` |
| `passkeyEnabledForTransactions` | `boolean` | `-` |
| `appPin` | `AppPinStatusDTO` | `-` |
| `requiredFactors` | `List<String>` | `-` |
| `passkeys` | `PasskeyInventoryDTO` | `-` |

### AccountSecurityStatusDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AccountSecurityStatusDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `passwordConfigured` | `boolean` | `-` |
| `passkeyRegistered` | `boolean` | `-` |
| `totpEnabled` | `boolean` | `-` |
| `backupCodesRemaining` | `int` | `-` |
| `unprotected` | `boolean` | `-` |
| `warningMessage` | `String` | `-` |
| `accountActivated` | `boolean` | `-` |
| `inboundEnabled` | `boolean` | `-` |
| `passkeys` | `PasskeyInventoryDTO` | `-` |

### AccountSecurityUpdateRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AccountSecurityUpdateRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `accountSecurity` | `AccountSecurityType` | `-` |
| `shamirTotalShares` | `Integer` | `-` |
| `shamirThreshold` | `Integer` | `-` |
| `multisigThreshold` | `Integer` | `-` |

### AdminAccessAttemptDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminAccessAttemptDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `attemptId` | `UUID` | `-` |
| `status` | `String` | `-` |
| `deviceId` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `browser` | `String` | `-` |
| `userAgent` | `String` | `-` |
| `ipFingerprint` | `String` | `-` |
| `requestedAt` | `LocalDateTime` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |

### AdminAccessDecisionRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminAccessDecisionRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `decision` | `String` | `-` |

### AdminAuthenticatedDeviceDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminAuthenticatedDeviceDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `deviceId` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `browser` | `String` | `-` |
| `userAgent` | `String` | `-` |
| `status` | `String` | `-` |
| `firstAccessAt` | `LocalDateTime` | `-` |
| `lastAccessAt` | `LocalDateTime` | `-` |

### AdminDeviceSessionDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminDeviceSessionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `UUID` | `-` |
| `deviceId` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `browser` | `String` | `-` |
| `platform` | `String` | `-` |
| `status` | `String` | `-` |
| `firstAccessAt` | `LocalDateTime` | `-` |
| `lastAccessAt` | `LocalDateTime` | `-` |

### AdminKeyCreateRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminKeyCreateRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `keyMaterialHash` | `String` | `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` |
| `deviceInstallId` | `String` | `-` |

### AdminKeyStatusDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminKeyStatusDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `configured` | `boolean` | `-` |
| `status` | `String` | `-` |
| `fingerprint` | `String` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `revokedAt` | `LocalDateTime` | `-` |

### AdminLoginRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminLoginRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `username` | `String` | `-` |
| `password` | `char[]` | `@JsonAlias({"passphrase"}) @JsonProperty("password")` |
| `adminKeyProof` | `String` | `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` |
| `deviceId` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `browser` | `String` | `-` |
| `userAgent` | `String` | `-` |
| `platform` | `String` | `-` |

### AdminLoginResponseDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AdminLoginResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `status` | `String` | `-` |
| `requiresMobileApproval` | `boolean` | `-` |
| `attemptId` | `UUID` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |
| `token` | `String` | `-` |
| `message` | `String` | `-` |

### AppPinStatusDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/AppPinStatusDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `enabled` | `boolean` | `-` |
| `configured` | `boolean` | `-` |
| `locked` | `boolean` | `-` |
| `failedAttempts` | `int` | `-` |
| `remainingAttempts` | `int` | `-` |
| `maxAttempts` | `int` | `-` |
| `minPinLength` | `int` | `-` |
| `maxPinLength` | `int` | `-` |
| `resettableWithTotp` | `boolean` | `-` |
| `deviceScoped` | `boolean` | `-` |
| `lockedUntil` | `LocalDateTime` | `-` |
| `lastVerifiedAt` | `LocalDateTime` | `-` |
| `updatedAt` | `LocalDateTime` | `-` |

### Assets

Source: `backend/kerosene/src/main/java/source/kfe/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `hotWalletBtc` | `BigDecimal` | `-` |
| `treasuryXpubOnchainBtc` | `BigDecimal` | `-` |
| `lightningBtc` | `BigDecimal` | `-` |
| `totalOnchainBtc` | `BigDecimal` | `-` |
| `totalAssetsBtc` | `BigDecimal` | `-` |

### BackupCodesStatusDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/BackupCodesStatusDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `enabled` | `boolean` | `-` |
| `remainingCodes` | `int` | `-` |
| `newlyGeneratedCodes` | `List<String>` | `-` |

### ChainState

Source: `backend/kerosene/src/main/java/source/kfe/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `bitcoinNetwork` | `String` | `-` |
| `bitcoinBlockHeight` | `Long` | `-` |
| `bitcoinBestBlockHashRef` | `String` | `-` |
| `lightningBlockHeight` | `Long` | `-` |
| `lightningBlockHashRef` | `String` | `-` |

### ConfigureAppPinRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/ConfigureAppPinRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `enabled` | `Boolean` | `-` |
| `pin` | `String` | `-` |
| `currentPin` | `String` | `-` |
| `totpCode` | `String` | `-` |

### DeviceTokenRegisterRequest

Source: `backend/kerosene/src/main/java/source/notification/dto/DeviceTokenRegisterRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `platform` | `String` | `-` |
| `token` | `String` | `-` |
| `deviceId` | `String` | `-` |
| `appVersion` | `String` | `-` |

### DeviceTokenResponse

Source: `backend/kerosene/src/main/java/source/notification/dto/DeviceTokenResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `Long` | `-` |
| `platform` | `String` | `-` |
| `tokenRef` | `String` | `-` |
| `deviceRef` | `String` | `-` |
| `appVersion` | `String` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `lastSeenAt` | `LocalDateTime` | `-` |
| `revokedAt` | `LocalDateTime` | `-` |
| `active` | `boolean` | `-` |

### EmergencyRecoveryFinishRequest

Source: `backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryFinishRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `recoverySessionId` | `String` | `-` |
| `totpCode` | `String` | `@JsonProperty(access = Access.WRITE_ONLY)` |
| `publicKey` | `String` | `-` |
| `publicKeyCose` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `signature` | `String` | `-` |
| `authData` | `String` | `-` |
| `clientDataJSON` | `String` | `-` |
| `credentialId` | `String` | `-` |
| `userHandle` | `String` | `-` |

### EmergencyRecoveryFinishResponse

Source: `backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryFinishResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `username` | `String` | `-` |
| `newBackupCodes` | `List<String>` | `-` |

### EmergencyRecoveryStartRequest

Source: `backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryStartRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `username` | `String` | `-` |
| `newPassphrase` | `char[]` | `@JsonProperty(access = Access.WRITE_ONLY)` |
| `recoveryCodes` | `List<String>` | `@JsonProperty(access = Access.WRITE_ONLY)` |
| `challenge` | `String` | `-` |
| `nonce` | `String` | `-` |

### EmergencyRecoveryStartResponse

Source: `backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryStartResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `recoverySessionId` | `String` | `-` |
| `otpUri` | `String` | `-` |
| `passkeyChallenge` | `String` | `-` |
| `expiresInSeconds` | `long` | `-` |
| `requiredRecoveryCodes` | `int` | `-` |

### Liabilities

Source: `backend/kerosene/src/main/java/source/kfe/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `internalLedgerBtc` | `BigDecimal` | `-` |
| `reservedOnchainBtc` | `BigDecimal` | `-` |
| `reservedLightningBtc` | `BigDecimal` | `-` |
| `totalOperationalExposureBtc` | `BigDecimal` | `-` |

### MerkleProof

Source: `backend/kerosene/src/main/java/source/kfe/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `merkleRoot` | `String` | `-` |
| `ledgerCount` | `Long` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `anchorTxidRef` | `String` | `-` |

### MiningAllocationRequestDTO

Source: `backend/kerosene/src/main/java/source/mining/dto/MiningAllocationRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `walletName` | `String` | `-` |
| `rigId` | `Long` | `-` |
| `requestedHashrate` | `BigDecimal` | `-` |
| `budgetBtc` | `BigDecimal` | `-` |
| `durationHours` | `Integer` | `-` |
| `payoutAddress` | `String` | `-` |
| `poolUrl` | `String` | `-` |
| `workerName` | `String` | `-` |
| `totpCode` | `String` | `-` |
| `passkeyAssertionResponseJSON` | `String` | `-` |
| `confirmationPassphrase` | `String` | `-` |

### MiningAllocationResponseDTO

Source: `backend/kerosene/src/main/java/source/mining/dto/MiningAllocationResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `UUID` | `-` |
| `rigId` | `Long` | `-` |
| `rigName` | `String` | `-` |
| `walletName` | `String` | `-` |
| `algorithm` | `String` | `-` |
| `allocatedHashrate` | `BigDecimal` | `-` |
| `hashUnit` | `String` | `-` |
| `durationHours` | `Integer` | `-` |
| `rentalCostBtc` | `BigDecimal` | `-` |
| `projectedGrossYieldBtc` | `BigDecimal` | `-` |
| `projectedNetYieldBtc` | `BigDecimal` | `-` |
| `refundedAmountBtc` | `BigDecimal` | `-` |
| `status` | `String` | `-` |
| `providerRentalReference` | `String` | `-` |
| `payoutAddress` | `String` | `-` |
| `poolUrl` | `String` | `-` |
| `workerName` | `String` | `-` |
| `startsAt` | `LocalDateTime` | `-` |
| `endsAt` | `LocalDateTime` | `-` |
| `settledAt` | `LocalDateTime` | `-` |

### MiningRigOfferDTO

Source: `backend/kerosene/src/main/java/source/mining/dto/MiningRigOfferDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `Long` | `-` |
| `rigCode` | `String` | `-` |
| `displayName` | `String` | `-` |
| `algorithm` | `String` | `-` |
| `hashUnit` | `String` | `-` |
| `availableHashrate` | `BigDecimal` | `-` |
| `pricePerUnitDayBtc` | `BigDecimal` | `-` |
| `projectedBtcYieldPerUnitDay` | `BigDecimal` | `-` |
| `minRentalHours` | `Integer` | `-` |
| `maxRentalHours` | `Integer` | `-` |
| `provider` | `String` | `-` |

### NotificationSendRequest

Source: `backend/kerosene/src/main/java/source/notification/dto/NotificationSendRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `userId` | `String` | `-` |
| `title` | `String` | `-` |
| `body` | `String` | `-` |
| `kind` | `String` | `-` |
| `severity` | `String` | `-` |
| `deeplink` | `String` | `-` |
| `entityType` | `String` | `-` |
| `entityId` | `String` | `-` |
| `metadata` | `Map<String, String>` | `-` |

### PasskeyActionRequiredDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/PasskeyActionRequiredDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `action` | `String` | `-` |
| `reason` | `String` | `-` |
| `challenge` | `String` | `-` |
| `totpFallbackAvailable` | `boolean` | `-` |
| `linkNewPasskeyAllowed` | `boolean` | `-` |
| `linkPasskeyPath` | `String` | `-` |
| `guidance` | `String` | `-` |
| `passkeys` | `PasskeyInventoryDTO` | `-` |

### PasskeyDeviceDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/PasskeyDeviceDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `credentialRef` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `brand` | `String` | `-` |
| `model` | `String` | `-` |
| `serialNumber` | `String` | `-` |
| `deviceInstallId` | `String` | `-` |
| `platform` | `String` | `-` |
| `browser` | `String` | `-` |
| `firstAccessAt` | `LocalDateTime` | `-` |
| `lastAccessAt` | `LocalDateTime` | `-` |
| `status` | `String` | `-` |
| `relyingPartyId` | `String` | `-` |
| `originHost` | `String` | `-` |
| `compatibilityStatus` | `String` | `-` |
| `compatibleWithCurrentLogin` | `boolean` | `-` |

### PasskeyInventoryDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/PasskeyInventoryDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `passkeyRegistered` | `boolean` | `-` |
| `compatibleForCurrentLogin` | `boolean` | `-` |
| `legacyCredentialsPresent` | `boolean` | `-` |
| `currentRelyingPartyId` | `String` | `-` |
| `currentHost` | `String` | `-` |
| `devices` | `List<PasskeyDeviceDTO>` | `-` |

### PasskeyRegistrationRequest

Source: `backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `publicKey` | `String` | `-` |
| `deviceName` | `String` | `-` |
| `signature` | `String` | `-` |
| `authData` | `String` | `-` |
| `clientDataJSON` | `String` | `-` |
| `credentialId` | `String` | `-` |
| `userHandle` | `String` | `-` |
| `publicKeyCose` | `String` | `-` |
| `brand` | `String` | `-` |
| `model` | `String` | `-` |
| `serialNumber` | `String` | `-` |
| `deviceInstallId` | `String` | `-` |
| `platform` | `String` | `-` |
| `browser` | `String` | `-` |
| `status` | `String` | `-` |

### PasskeyVerifyRequest

Source: `backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `username` | `String` | `-` |
| `signature` | `String` | `-` |
| `authData` | `String` | `-` |
| `clientDataJSON` | `String` | `-` |
| `credentialId` | `String` | `-` |

### ProviderHealth

Source: `backend/kerosene/src/main/java/source/kfe/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `provider` | `String` | `-` |
| `status` | `String` | `-` |
| `source` | `String` | `-` |
| `message` | `String` | `-` |

### ResponseError

Source: `backend/kerosene/src/main/java/source/auth/dto/ResponseError.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `timestamp` | `LocalDateTime` | `-` |
| `status` | `HttpStatus` | `-` |
| `error` | `String` | `-` |
| `message` | `String` | `-` |
| `path` | `String` | `-` |

### SignupResponseDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/SignupResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `sessionId` | `String` | `-` |
| `otpUri` | `String` | `-` |
| `backupCodes` | `List<String>` | `-` |
| `totpOptional` | `boolean` | `-` |

### TotpSetupResponseDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/TotpSetupResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `otpUri` | `String` | `-` |
| `secret` | `String` | `-` |

### KfeReserveOverviewResponse

Source: `backend/kerosene/src/main/java/source/kfe/dto/KfeReserveOverviewResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `totalOnchainBtc` | `BigDecimal` | `-` |
| `lightningNodeBtc` | `BigDecimal` | `-` |
| `inboundLiquidityBtc` | `BigDecimal` | `-` |
| `outboundLiquidityBtc` | `BigDecimal` | `-` |
| `reservedOnchainBtc` | `BigDecimal` | `-` |
| `reservedLightningBtc` | `BigDecimal` | `-` |
| `availableOnchainBtc` | `BigDecimal` | `-` |
| `availableLightningBtc` | `BigDecimal` | `-` |
| `lightningSendsAllowed` | `boolean` | `-` |
| `liquidityState` | `String` | `-` |

### VerifyAppPinRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/VerifyAppPinRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `pin` | `String` | `-` |
