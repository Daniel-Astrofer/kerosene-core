# Kerosene Backend API Reference

Referencia operacional dos endpoints HTTP expostos pelo backend Kerosene. O inventario foi derivado dos controllers Spring em `backend/kerosene/src/main/java/source/**`, da cadeia de seguranca, dos filtros HTTP e dos DTOs Java. Cada endpoint declara autenticacao, headers, parametros, corpo de request e formato de response para que a rota possa ser chamada sem depender de uma tabela global.

> Referencia separada por dominio: [api/README.md](api/README.md). Este arquivo permanece como referencia consolidada e base de auditoria full-text.

## Escopo e cobertura

- Secoes de endpoint HTTP documentadas: `162` (`161` pares metodo/path unicos; `GET /` tem variante JSON e HTML por content negotiation).
- Inclui controllers REST, rotas HTML servidas pelo backend, webhook BTCPay e `POST /audit/trigger`, que usa anotacao fully-qualified no codigo.
- WebSocket/STOMP e Actuator aparecem em secoes proprias porque nao sao metodos REST de controller de dominio.
- O formato de erro padrao e `ApiResponse` com `success=false`, `message`, `errorCode`, `data` opcional e `timestamp`; filtros tambem podem retornar erro sem envelope em `413`, `415` e alguns `401/403` do Spring Security.
- Contagem verificada por headings `### <METHOD> <PATH>` neste documento: `162` secoes, `161` pares unicos.

## Regras HTTP globais

- Bodies em `POST`, `PUT`, `PATCH` e `DELETE` com corpo precisam usar `Content-Type: application/json`, salvo rotas HTML.
- O filtro paranoico limita o corpo padrao a `2048` bytes; rotas de PSBT aceitam ate `64 KiB`.
- `Digest: SHA-256=<base64>` e opcional, mas se enviado o hash precisa bater com o body.
- `Authorization: Bearer <jwt>` e aceito apenas em rotas protegidas; rotas publicas de auth nao precisam dele.
- JWT proximo da expiracao pode voltar renovado no header `X-New-Token`.
- CORS permite `Authorization`, `Content-Type`, `Digest`, `X-Correlation-Id`, `X-Request-Id`, `X-Idempotency-Key`, `Idempotency-Key`, `X-Admin-Token`, `X-Owner-TOTP`, `X-Hardware-Signature`, headers de release attestation e `X-Device-Hash`.

## Endpoints
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

### GET /api/economy/btc-price

- Controller: `EconomyController.getBtcPrice` (`backend/kerosene/src/main/java/source/transactions/controller/EconomyController.java: 48`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
    "btcUsd": "60000.00",
    "btcBrl": "300000.00",
    "usdBrl": "5.00000000"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/economy/status

- Controller: `EconomyController.getEconomyStatus` (`backend/kerosene/src/main/java/source/transactions/controller/EconomyController.java: 34`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
    "withdrawalFeeSats": 10000,
    "withdrawalStatus": "ENABLED"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /api/onramp/urls

- Controller: `OnrampController.getOnrampUrls` (`backend/kerosene/src/main/java/source/transactions/controller/OnrampController.java: 36`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, String>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `walletName` | `String` | false | `` |
| `amountBtc` | `BigDecimal` | false | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "moonpay": "https://buy.moonpay.com?currencyCode=btc&walletAddress=bc1qexampleaddress",
    "banxa": "https://checkout.banxa.com?coinType=BTC&walletAddress=bc1qexampleaddress",
    "bipa": "https://bipa.app/buy/btc?address=bc1qexampleaddress",
    "transferId": "00000000-0000-0000-0000-000000000000",
    "depositAddress": "bc1qexampleaddress",
    "walletName": "default"
  },
  "timestamp": "2026-01-01T00:00:00"
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

## Audit

### GET /audit/history

- Controller: `MerkleAuditController.history` (`backend/kerosene/src/main/java/source/ledger/audit/MerkleAuditController.java: 56`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<?>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `10` | `int` | false | `10` |

Request body:

Nenhum.

Response body:

```json
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /audit/latest-root

- Controller: `MerkleAuditController.latestRoot` (`backend/kerosene/src/main/java/source/ledger/audit/MerkleAuditController.java: 40`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<?>`.

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
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /audit/trigger

- Controller: `MerkleAuditController.triggerAudit` (`backend/kerosene/src/main/java/source/ledger/audit/MerkleAuditController.java: 72`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<?>`.

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
"string"
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

## Bitcoin Accounts

### GET /bitcoin/accounts

- Controller: `BitcoinAccountsController.list` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 48`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<Map<String, Object>>>>`.

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
      "type": "INTERNAL_CARD",
      "custody": "KEROSENE_CUSTODIAL",
      "status": "ACTIVE",
      "label": "Internal BTC Card",
      "riskTier": "BRONZE",
      "createdAt": "2026-01-01T00:00:00",
      "cardId": "00000000-0000-0000-0000-000000000000",
      "ledgerAccountId": "00000000-0000-0000-0000-000000000000",
      "dailyLimitSats": 5000000,
      "monthlyLimitSats": 50000000,
      "cardStatus": "ACTIVE",
      "balanceAvailableSats": 1000,
      "balancePendingSats": 0,
      "balanceLockedSats": 0,
      "balanceAutoHoldSats": 0
    },
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "type": "WATCH_ONLY_COLD_WALLET",
      "custody": "WATCH_ONLY",
      "status": "ACTIVE",
      "label": "Cold Wallet",
      "riskTier": "WATCH_ONLY",
      "createdAt": "2026-01-01T00:00:00",
      "coldWalletId": "00000000-0000-0000-0000-000000000000",
      "observedBalanceSats": 1000,
      "scriptPolicy": "SINGLE_SIG",
      "canSign": false,
      "derivationPath": "m/84'/0'/0'",
      "fingerprint": "abcd1234"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/accounts/cold-wallet

- Controller: `BitcoinAccountsController.createColdWallet` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 64`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
  "label": "string",
  "descriptor": "string",
  "xpub": "string",
  "fingerprint": "string",
  "derivationPath": "string",
  "scriptPolicy": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "type": "WATCH_ONLY_COLD_WALLET",
    "custody": "WATCH_ONLY",
    "status": "ACTIVE",
    "label": "Cold Wallet",
    "riskTier": "WATCH_ONLY",
    "createdAt": "2026-01-01T00:00:00",
    "coldWalletId": "00000000-0000-0000-0000-000000000000",
    "observedBalanceSats": 1000,
    "scriptPolicy": "SINGLE_SIG",
    "canSign": false,
    "derivationPath": "m/84'/0'/0'",
    "fingerprint": "abcd1234"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/accounts/internal-card

- Controller: `BitcoinAccountsController.createInternalCard` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 55`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
  "label": "string",
  "riskTier": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "type": "INTERNAL_CARD",
    "custody": "KEROSENE_CUSTODIAL",
    "status": "ACTIVE",
    "label": "Internal BTC Card",
    "riskTier": "BRONZE",
    "createdAt": "2026-01-01T00:00:00",
    "cardId": "00000000-0000-0000-0000-000000000000",
    "ledgerAccountId": "00000000-0000-0000-0000-000000000000",
    "dailyLimitSats": 5000000,
    "monthlyLimitSats": 50000000,
    "cardStatus": "ACTIVE",
    "balanceAvailableSats": 1000,
    "balancePendingSats": 0,
    "balanceLockedSats": 0,
    "balanceAutoHoldSats": 0
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/accounts/{accountId}/receive-requests

- Controller: `BitcoinAccountsController.createReceiveRequest` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 81`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
| `accountId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "amountSats": "0.00010000",
  "expiry": "string",
  "oneTime": true
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "publicCode": "KRS-example",
    "amountSats": 10000,
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": null,
    "oneTime": true,
    "status": "ACTIVE",
    "network": "mainnet",
    "address": "bc1qexampleaddress",
    "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
    "minimumConfirmations": 3,
    "nextAction": "NONE",
    "accountId": "00000000-0000-0000-0000-000000000000",
    "cardId": "00000000-0000-0000-0000-000000000000",
    "addressId": "00000000-0000-0000-0000-000000000000",
    "selfServiceReason": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/accounts/{accountId}/receive-requests

- Controller: `BitcoinAccountsController.listReceiveRequests` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<Map<String, Object>>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `accountId` | `string` | yes |

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
      "publicCode": "KRS-example",
      "amountSats": 10000,
      "expiresAt": "2026-01-01T00:00:00",
      "createdAt": "2026-01-01T00:00:00",
      "paidAt": null,
      "oneTime": true,
      "status": "ACTIVE",
      "network": "mainnet",
      "address": "bc1qexampleaddress",
      "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
      "minimumConfirmations": 3,
      "nextAction": "NONE",
      "accountId": "00000000-0000-0000-0000-000000000000",
      "cardId": "00000000-0000-0000-0000-000000000000",
      "addressId": "00000000-0000-0000-0000-000000000000",
      "selfServiceReason": null
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Observacoes: retorna ate 50 solicitacoes mais recentes da conta interna, omitindo itens ocultos. Solicitacoes `ACTIVE` vencidas sao marcadas como `EXPIRED` antes da resposta.

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/cold-wallets/{coldWalletId}/psbt

- Controller: `BitcoinAccountsController.listColdWalletPsbt` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 165`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<Map<String, Object>>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `coldWalletId` | `string` | yes |

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
      "coldWalletId": "00000000-0000-0000-0000-000000000000",
      "unsignedPsbt": "cHNidP8BAHECAAAAA...",
      "status": "WAITING_EXTERNAL_SIGNATURE",
      "destinationAddress": "bc1qexampleaddress",
      "amountSats": 10000,
      "estimatedFeeSats": 500,
      "broadcastTxid": "txid-or-null",
      "broadcastTxidRef": "abcd1234...ef567890",
      "expiresAt": "2026-01-01T00:00:00",
      "createdAt": "2026-01-01T00:00:00"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/cold-wallets/{coldWalletId}/psbt

- Controller: `BitcoinAccountsController.createPsbt` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 140`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
| `coldWalletId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "destinationAddress": "bc1qexampleaddress",
  "amountSats": "0.00010000",
  "feeRate": "0.00010000",
  "selectedUtxoIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "coldWalletId": "00000000-0000-0000-0000-000000000000",
    "unsignedPsbt": "cHNidP8BAHECAAAAA...",
    "status": "WAITING_EXTERNAL_SIGNATURE",
    "destinationAddress": "bc1qexampleaddress",
    "amountSats": 10000,
    "estimatedFeeSats": 500,
    "broadcastTxid": "txid-or-null",
    "broadcastTxidRef": "abcd1234...ef567890",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/cold-wallets/{coldWalletId}/utxos

- Controller: `BitcoinAccountsController.listColdWalletUtxos` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 156`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<Map<String, Object>>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `coldWalletId` | `string` | yes |

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
      "txidRef": "abcd1234...ef567890",
      "vout": 0,
      "amountSats": 10000,
      "confirmations": 6,
      "status": "UNSPENT"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/psbt/{workflowId}

- Controller: `BitcoinAccountsController.getPsbt` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 188`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `workflowId` | `string` | yes |

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
    "coldWalletId": "00000000-0000-0000-0000-000000000000",
    "unsignedPsbt": "cHNidP8BAHECAAAAA...",
    "status": "WAITING_EXTERNAL_SIGNATURE",
    "destinationAddress": "bc1qexampleaddress",
    "amountSats": 10000,
    "estimatedFeeSats": 500,
    "broadcastTxid": "txid-or-null",
    "broadcastTxidRef": "abcd1234...ef567890",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/psbt/{workflowId}/signed

- Controller: `BitcoinAccountsController.submitSignedPsbt` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 174`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
| `workflowId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "signedPsbt": "string",
  "broadcast": true
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "coldWalletId": "00000000-0000-0000-0000-000000000000",
    "unsignedPsbt": "cHNidP8BAHECAAAAA...",
    "status": "WAITING_EXTERNAL_SIGNATURE",
    "destinationAddress": "bc1qexampleaddress",
    "amountSats": 10000,
    "estimatedFeeSats": 500,
    "broadcastTxid": "txid-or-null",
    "broadcastTxidRef": "abcd1234...ef567890",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/receive-requests/{id}/expire

- Controller: `BitcoinAccountsController.expireReceiveRequest` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 121`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "publicCode": "KRS-example",
    "amountSats": 10000,
    "expiresAt": "2026-01-01T00:00:00",
    "oneTime": true,
    "status": "ACTIVE",
    "network": "mainnet",
    "address": "bc1qexampleaddress",
    "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
    "minimumConfirmations": 3,
    "nextAction": "NONE",
    "cardId": "00000000-0000-0000-0000-000000000000",
    "addressId": "00000000-0000-0000-0000-000000000000",
    "selfServiceReason": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/receive-requests/{id}/hide

- Controller: `BitcoinAccountsController.hideReceiveRequest` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 112`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "publicCode": "KRS-example",
    "amountSats": 10000,
    "expiresAt": "2026-01-01T00:00:00",
    "oneTime": true,
    "status": "ACTIVE",
    "network": "mainnet",
    "address": "bc1qexampleaddress",
    "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
    "minimumConfirmations": 3,
    "nextAction": "NONE",
    "cardId": "00000000-0000-0000-0000-000000000000",
    "addressId": "00000000-0000-0000-0000-000000000000",
    "selfServiceReason": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/receive-requests/{id}/status

- Controller: `BitcoinAccountsController.receiveStatus` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 103`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "publicCode": "KRS-example",
    "amountSats": 10000,
    "expiresAt": "2026-01-01T00:00:00",
    "oneTime": true,
    "status": "ACTIVE",
    "network": "mainnet",
    "address": "bc1qexampleaddress",
    "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
    "minimumConfirmations": 3,
    "nextAction": "NONE",
    "cardId": "00000000-0000-0000-0000-000000000000",
    "addressId": "00000000-0000-0000-0000-000000000000",
    "selfServiceReason": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/receive-requests/{id}/user-action

- Controller: `BitcoinAccountsController.receiveUserAction` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 130`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
| `id` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "action": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "publicCode": "KRS-example",
    "amountSats": 10000,
    "expiresAt": "2026-01-01T00:00:00",
    "oneTime": true,
    "status": "ACTIVE",
    "network": "mainnet",
    "address": "bc1qexampleaddress",
    "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
    "minimumConfirmations": 3,
    "nextAction": "NONE",
    "cardId": "00000000-0000-0000-0000-000000000000",
    "addressId": "00000000-0000-0000-0000-000000000000",
    "selfServiceReason": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/receive/{publicCode}

- Controller: `BitcoinAccountsController.publicReceive` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 96`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `publicCode` | `string` | yes |

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
    "publicCode": "KRS-example",
    "amountSats": 10000,
    "expiresAt": "2026-01-01T00:00:00",
    "oneTime": true,
    "status": "ACTIVE",
    "network": "mainnet",
    "address": "bc1qexampleaddress",
    "bip21": "bitcoin:bc1qexampleaddress?amount=0.00010000",
    "minimumConfirmations": 3,
    "nextAction": "NONE"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/tax-events

- Controller: `BitcoinAccountsController.listTaxEvents` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 197`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<Map<String, Object>>>>`.

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
      "createdAt": "2026-01-01T00:00:00",
      "eventType": "DEPOSIT_INTERNAL",
      "asset": "BTC",
      "quantitySats": 10000,
      "classification": "USER_CLASSIFICATION_PENDING",
      "sourceRef": "abcd1234...ef567890:0",
      "accountId": "00000000-0000-0000-0000-000000000000",
      "cardId": "00000000-0000-0000-0000-000000000000",
      "walletId": "00000000-0000-0000-0000-000000000000",
      "purgeAfter": "2026-01-01T00:00:00"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /bitcoin/tax-events/export

- Controller: `BitcoinAccountsController.exportTaxEvents` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 204`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `json` | `String` | false | `json` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "format": "json",
    "filename": "kerosene-tax-events.json",
    "educationalNotice": "Organizamos seus eventos para facilitar sua conferencia. Este relatorio nao substitui orientacao profissional.",
    "events": [
      {
        "id": "00000000-0000-0000-0000-000000000000",
        "createdAt": "2026-01-01T00:00:00",
        "eventType": "DEPOSIT_INTERNAL",
        "asset": "BTC",
        "quantitySats": 10000,
        "classification": "USER_CLASSIFICATION_PENDING",
        "sourceRef": "abcd1234...ef567890:0",
        "accountId": "00000000-0000-0000-0000-000000000000",
        "cardId": "00000000-0000-0000-0000-000000000000",
        "walletId": "00000000-0000-0000-0000-000000000000",
        "purgeAfter": "2026-01-01T00:00:00"
      }
    ],
    "content": "created_at,event_type,asset,quantity_sats,classification,source_ref,account_id,card_id,wallet_id\\n"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /bitcoin/tax-events/{eventId}/classify

- Controller: `BitcoinAccountsController.classifyTaxEvent` (`backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java: 213`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<Map<String, Object>>>`.

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
| `eventId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "classification": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "00000000-0000-0000-0000-000000000000",
    "classification": "SELF_TRANSFER"
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

## Transactions

### POST /deposit/{transferId}/cancel

- Controller: `DepositController.cancelDeposit` (`backend/kerosene/src/main/java/source/transactions/controller/DepositController.java: 25`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<ExternalTransferResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `transferId` | `string` | yes |

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
    "network": "string",
    "transferType": "string",
    "status": "string",
    "provider": "string",
    "walletName": "string",
    "destination": "string",
    "invoiceId": "string",
    "blockchainTxid": "string",
    "paymentHash": "string",
    "invoiceData": "string",
    "expectedAmountBtc": "0.00010000",
    "amountBtc": "0.00010000",
    "networkFeeBtc": "0.00010000",
    "platformFeeBtc": "0.00010000",
    "totalDebitedBtc": "0.00010000",
    "externalReference": "string",
    "confirmations": 1,
    "expiresAt": "2026-01-01T00:00:00",
    "detectedAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
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

## Integrations

### POST /integrations/btcpay/webhook/{storeId}

- Controller: `BtcPayWebhookController.receiveWebhook` (`backend/kerosene/src/main/java/source/transactions/controller/BtcPayWebhookController.java: 24`).
- Autenticacao: `Publico`.
- Response Java: `ResponseEntity<Void>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `BTCPAY-SIG` | no | `<String>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `storeId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "type": "InvoiceSettled",
  "invoiceId": "invoice-id",
  "metadata": {}
}
```

Response body:

Sem corpo.

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

## Ledger

### GET /ledger/all

- Controller: `LedgerController.getAllLedgers` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 115`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<LedgerDTO>>>`.

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
      "walletId": 1,
      "walletName": "string",
      "balance": "0.00010000",
      "nonce": 1,
      "lastHash": "string",
      "context": "string",
      "amount": "0.00010000"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /ledger/balance

- Controller: `LedgerController.getBalance` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 139`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<BigDecimal>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `walletName` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": "0.00010000",
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /ledger/find

- Controller: `LedgerController.getLedgerByWalletName` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 124`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<LedgerDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `walletName` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": 1,
    "walletId": 1,
    "walletName": "string",
    "balance": "0.00010000",
    "nonce": 1,
    "lastHash": "string",
    "context": "string",
    "amount": "0.00010000"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /ledger/history

- Controller: `LedgerController.getHistory` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 99`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<LedgerSyncEventDTO>>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `0` | `int` | false | `0` |
| `50` | `int` | false | `50` |

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
      "transactionType": "string",
      "amount": "0.00010000",
      "status": "string",
      "networkFee": "0.00010000",
      "txidFingerprint": "string",
      "createdAt": "2026-01-01T00:00:00",
      "confirmations": 1
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /ledger/payment-request

- Controller: `LedgerController.createPaymentRequest` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 236`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<InternalPaymentRequestDTO>>`.

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
  "amount": "0.00010000",
  "receiverWalletName": "main"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "string",
    "requesterUserId": 1,
    "receiverWalletId": 1,
    "receiverWalletName": "string",
    "destinationHash": "string",
    "amount": "0.00010000",
    "status": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /ledger/payment-request/{linkId}

- Controller: `LedgerController.getPaymentRequest` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 245`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentRequestPublicDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `linkId` | `string` | yes |

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
    "id": "string",
    "amount": "0.00010000",
    "status": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "destinationHash": "string",
    "locked": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /ledger/payment-request/{linkId}/pay

- Controller: `LedgerController.payPaymentRequest` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 256`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<InternalPaymentRequestDTO>>`.

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
  "idempotencyKey": "string",
  "payerWalletName": "main",
  "totpCode": "123456",
  "passkeyAssertionJson": "string",
  "confirmationPassphrase": "correct-horse-battery-staple"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "string",
    "requesterUserId": 1,
    "receiverWalletId": 1,
    "receiverWalletName": "string",
    "destinationHash": "string",
    "amount": "0.00010000",
    "status": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /ledger/transaction

- Controller: `LedgerController.transaction` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java: 71`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<InternalTransactionResponseDTO>>`.

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
  "sender": "string",
  "receiver": "string",
  "amount": "0.00010000",
  "context": "string",
  "idempotencyKey": "string",
  "requestTimestamp": 1,
  "passkeyAssertionJson": "string",
  "confirmationPassphrase": "correct-horse-battery-staple",
  "totpCode": "123456"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "txid": "string",
    "status": "string",
    "amount": "0.00010000",
    "sender": "string",
    "receiver": "string",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

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

## Payments

### POST /payments/quote

- Controller: `PaymentsController.quote` (`backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java: 40`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentQuoteResponse>>`.

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
  "rail": "INTERNAL",
  "feeMode": "0.00010000",
  "amountFiat": "0.00010000",
  "fiatCurrency": "string",
  "asset": "string",
  "receiverIdentifier": "string",
  "externalDestination": "bc1qexampleaddress",
  "speed": "NORMAL"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "paymentIntentId": "00000000-0000-0000-0000-000000000000",
    "quoteExpiresAt": "2026-01-01T00:00:00Z",
    "rail": "ACTIVE",
    "feeMode": "ACTIVE",
    "receiverDisplayName": "string",
    "receiverAmountFiat": "string",
    "receiverAmountSats": 1,
    "totalDebitFiat": "string",
    "totalDebitSats": 1,
    "networkFeeFiat": "string",
    "networkFeeSats": 1,
    "keroseneFeeFiat": "string",
    "keroseneFeeSats": 1,
    "warnings": [
      "string"
    ],
    "requiresConfirmation": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /payments/{paymentIntentId}

- Controller: `PaymentsController.status` (`backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java: 60`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentStatusResponse>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `paymentIntentId` | `string` | yes |

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
    "paymentIntentId": "00000000-0000-0000-0000-000000000000",
    "status": "ACTIVE",
    "rail": "ACTIVE",
    "feeMode": "ACTIVE",
    "receiverDisplayName": "string",
    "receiverAmountSats": 1,
    "totalDebitSats": 1,
    "networkFeeSats": 1,
    "keroseneFeeSats": 1,
    "quoteExpiresAt": "2026-01-01T00:00:00Z",
    "failureCode": "string",
    "failureMessage": "string",
    "warnings": [
      "string"
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /payments/{paymentIntentId}/confirm

- Controller: `PaymentsController.confirm` (`backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java: 48`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentStatusResponse>>`.

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
| `paymentIntentId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "idempotencyKey": "string",
  "userConfirmationToken": "token",
  "acceptedTotalDebitSats": 1,
  "acceptedReceiverAmountSats": "0.00010000"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "paymentIntentId": "00000000-0000-0000-0000-000000000000",
    "status": "ACTIVE",
    "rail": "ACTIVE",
    "feeMode": "ACTIVE",
    "receiverDisplayName": "string",
    "receiverAmountSats": 1,
    "totalDebitSats": 1,
    "networkFeeSats": 1,
    "keroseneFeeSats": 1,
    "quoteExpiresAt": "2026-01-01T00:00:00Z",
    "failureCode": "string",
    "failureMessage": "string",
    "warnings": [
      "string"
    ]
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

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

## Transactions

### POST /transactions/broadcast

- Controller: `TransactionController.broadcastTransaction` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 149`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<TransactionResponseDTO>>`.

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
  "rawTxHex": "string",
  "toAddress": "bc1qexampleaddress",
  "amount": "0.00010000",
  "message": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "txid": "string",
    "status": "string",
    "feeSatoshis": 1,
    "amountReceived": "0.00010000",
    "sender": "string",
    "receiver": "string",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/create-payment-link

- Controller: `TransactionController.createPaymentLink` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 171`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentLinkDTO>>`.

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
  "amount": "0.00010000",
  "description": "string",
  "expiresInMinutes": "2026-01-01T00:00:00Z",
  "visibility": "string",
  "confirmationMode": "string",
  "amountLocked": "0.00010000",
  "referenceLabel": "string",
  "metadata": {
    "key": "value"
  }
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "string",
    "userId": 1,
    "sessionId": "string",
    "amountBtc": "0.00010000",
    "grossAmountBtc": "0.00010000",
    "depositFeeBtc": "0.00010000",
    "netAmountBtc": "0.00010000",
    "description": "string",
    "depositAddress": "string",
    "visibility": "string",
    "confirmationMode": "string",
    "amountLocked": true,
    "referenceLabel": "string",
    "metadata": {
      "key": "string"
    },
    "status": "string",
    "txid": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00",
    "completedAt": "2026-01-01T00:00:00",
    "cancelledAt": "2026-01-01T00:00:00",
    "cancelReason": "string",
    "paymentRail": "string",
    "paymentIntentStatus": "string",
    "settlementReference": "string",
    "terminal": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/create-unsigned

- Controller: `TransactionController.createUnsignedTransaction` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 116`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<UnsignedTransactionDTO>>`.

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
  "fromAddress": "bc1qexampleaddress",
  "toAddress": "bc1qexampleaddress",
  "amount": "0.00010000",
  "feeSatoshis": "0.00010000"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "rawTxHex": "string",
    "txId": "string",
    "inputs": [
      {
        "txid": "string",
        "vout": 1,
        "value": "0.00010000",
        "scriptPubKey": "string"
      }
    ],
    "outputs": [
      {
        "address": "string",
        "value": "0.00010000"
      }
    ],
    "totalAmount": "0.00010000",
    "fee": 1,
    "fromAddress": "string",
    "toAddress": "string",
    "txid": "string",
    "vout": 1,
    "value": "0.00010000",
    "scriptPubKey": "string",
    "address": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/deposit-address

- Controller: `TransactionController.getDepositAddress` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 67`).
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

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `expectedAmountBtc` | `BigDecimal` | false | `` |

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

### GET /transactions/estimate-fee

- Controller: `TransactionController.estimateFee` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 99`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<EstimatedFeeDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `amount` | `BigDecimal` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "fastSatoshisPerByte": 1,
    "standardSatoshisPerByte": 1,
    "slowSatoshisPerByte": 1,
    "estimatedFastBtc": "0.00010000",
    "estimatedStandardBtc": "0.00010000",
    "estimatedSlowBtc": "0.00010000",
    "amountReceived": "0.00010000",
    "totalToSend": "0.00010000"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/network/lightning/invoice

- Controller: `NetworkPaymentsController.createLightningInvoice` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 71`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<LightningInvoiceResponseDTO>>`.

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
  "idempotencyKey": "string",
  "walletName": "main",
  "amount": "0.00010000",
  "memo": "string",
  "expiresInSeconds": "2026-01-01T00:00:00Z"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "transferId": "00000000-0000-0000-0000-000000000000",
    "walletName": "string",
    "paymentRequest": "string",
    "paymentHash": "string",
    "lightningAddress": "string",
    "amountBtc": "0.00010000",
    "provider": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "status": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/network/lightning/pay

- Controller: `NetworkPaymentsController.payLightning` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 92`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<ExternalTransferResponseDTO>>`.

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
  "idempotencyKey": "string",
  "fromWalletName": "main",
  "paymentRequest": "string",
  "amount": "0.00010000",
  "maxRoutingFeeBtc": "0.00010000",
  "description": "string",
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
    "network": "string",
    "transferType": "string",
    "status": "string",
    "provider": "string",
    "walletName": "string",
    "destination": "string",
    "invoiceId": "string",
    "blockchainTxid": "string",
    "paymentHash": "string",
    "invoiceData": "string",
    "expectedAmountBtc": "0.00010000",
    "amountBtc": "0.00010000",
    "networkFeeBtc": "0.00010000",
    "platformFeeBtc": "0.00010000",
    "totalDebitedBtc": "0.00010000",
    "externalReference": "string",
    "confirmations": 1,
    "expiresAt": "2026-01-01T00:00:00",
    "detectedAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/network/onchain/address

- Controller: `NetworkPaymentsController.issueOnchainAddress` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 38`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<OnchainAddressAllocationDTO>>`.

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
  "expectedAmountBtc": "0.00010000"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "walletName": "string",
    "onchainAddress": "string",
    "expectedAmountBtc": "0.00010000",
    "network": "string",
    "provider": "string",
    "externalWalletReference": "string",
    "walletMode": "string",
    "transferId": "00000000-0000-0000-0000-000000000000",
    "transferStatus": "string",
    "confirmations": 1,
    "requiredConfirmations": 1,
    "blockchainTxid": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/network/onchain/send

- Controller: `NetworkPaymentsController.sendOnchain` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 59`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<ExternalTransferResponseDTO>>`.

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
  "idempotencyKey": "string",
  "fromWalletName": "main",
  "toAddress": "bc1qexampleaddress",
  "amount": "0.00010000",
  "description": "string",
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
    "network": "string",
    "transferType": "string",
    "status": "string",
    "provider": "string",
    "walletName": "string",
    "destination": "string",
    "invoiceId": "string",
    "blockchainTxid": "string",
    "paymentHash": "string",
    "invoiceData": "string",
    "expectedAmountBtc": "0.00010000",
    "amountBtc": "0.00010000",
    "networkFeeBtc": "0.00010000",
    "platformFeeBtc": "0.00010000",
    "totalDebitedBtc": "0.00010000",
    "externalReference": "string",
    "confirmations": 1,
    "expiresAt": "2026-01-01T00:00:00",
    "detectedAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/network/transfers

- Controller: `NetworkPaymentsController.listTransfers` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 104`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<ExternalTransferResponseDTO>>>`.

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
      "network": "string",
      "transferType": "string",
      "status": "string",
      "provider": "string",
      "walletName": "string",
      "destination": "string",
      "invoiceId": "string",
      "blockchainTxid": "string",
      "paymentHash": "string",
      "invoiceData": "string",
      "expectedAmountBtc": "0.00010000",
      "amountBtc": "0.00010000",
      "networkFeeBtc": "0.00010000",
      "platformFeeBtc": "0.00010000",
      "totalDebitedBtc": "0.00010000",
      "externalReference": "string",
      "confirmations": 1,
      "expiresAt": "2026-01-01T00:00:00",
      "detectedAt": "2026-01-01T00:00:00",
      "settledAt": "2026-01-01T00:00:00",
      "createdAt": "2026-01-01T00:00:00",
      "updatedAt": "2026-01-01T00:00:00",
      "context": "string"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/network/transfers/{transferId}

- Controller: `NetworkPaymentsController.getTransfer` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 111`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<ExternalTransferResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `transferId` | `string` | yes |

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
    "network": "string",
    "transferType": "string",
    "status": "string",
    "provider": "string",
    "walletName": "string",
    "destination": "string",
    "invoiceId": "string",
    "blockchainTxid": "string",
    "paymentHash": "string",
    "invoiceData": "string",
    "expectedAmountBtc": "0.00010000",
    "amountBtc": "0.00010000",
    "networkFeeBtc": "0.00010000",
    "platformFeeBtc": "0.00010000",
    "totalDebitedBtc": "0.00010000",
    "externalReference": "string",
    "confirmations": 1,
    "expiresAt": "2026-01-01T00:00:00",
    "detectedAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/network/transfers/{transferId}/cancel

- Controller: `NetworkPaymentsController.cancelInboundTransfer` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 82`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<ExternalTransferResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `transferId` | `string` | yes |

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
    "network": "string",
    "transferType": "string",
    "status": "string",
    "provider": "string",
    "walletName": "string",
    "destination": "string",
    "invoiceId": "string",
    "blockchainTxid": "string",
    "paymentHash": "string",
    "invoiceData": "string",
    "expectedAmountBtc": "0.00010000",
    "amountBtc": "0.00010000",
    "networkFeeBtc": "0.00010000",
    "platformFeeBtc": "0.00010000",
    "totalDebitedBtc": "0.00010000",
    "externalReference": "string",
    "confirmations": 1,
    "expiresAt": "2026-01-01T00:00:00",
    "detectedAt": "2026-01-01T00:00:00",
    "settledAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/network/wallet-profile

- Controller: `NetworkPaymentsController.getWalletNetworkProfile` (`backend/kerosene/src/main/java/source/transactions/controller/NetworkPaymentsController.java: 49`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<WalletNetworkAddressDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `walletName` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "walletName": "string",
    "onchainAddress": "string",
    "lightningAddress": "string",
    "network": "string",
    "provider": "string",
    "externalWalletReference": "string",
    "walletMode": "string",
    "lightningEnabled": true,
    "lightningUnavailableReason": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/payment-link/{linkId}

- Controller: `TransactionController.getPaymentLink` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 188`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentLinkDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `linkId` | `string` | yes |

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
    "id": "string",
    "userId": 1,
    "sessionId": "string",
    "amountBtc": "0.00010000",
    "grossAmountBtc": "0.00010000",
    "depositFeeBtc": "0.00010000",
    "netAmountBtc": "0.00010000",
    "description": "string",
    "depositAddress": "string",
    "visibility": "string",
    "confirmationMode": "string",
    "amountLocked": true,
    "referenceLabel": "string",
    "metadata": {
      "key": "string"
    },
    "status": "string",
    "txid": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00",
    "completedAt": "2026-01-01T00:00:00",
    "cancelledAt": "2026-01-01T00:00:00",
    "cancelReason": "string",
    "paymentRail": "string",
    "paymentIntentStatus": "string",
    "settlementReference": "string",
    "terminal": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/payment-link/{linkId}/cancel

- Controller: `TransactionController.cancelPayment` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 248`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentLinkDTO>>`.

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
  "reason": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "string",
    "userId": 1,
    "sessionId": "string",
    "amountBtc": "0.00010000",
    "grossAmountBtc": "0.00010000",
    "depositFeeBtc": "0.00010000",
    "netAmountBtc": "0.00010000",
    "description": "string",
    "depositAddress": "string",
    "visibility": "string",
    "confirmationMode": "string",
    "amountLocked": true,
    "referenceLabel": "string",
    "metadata": {
      "key": "string"
    },
    "status": "string",
    "txid": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00",
    "completedAt": "2026-01-01T00:00:00",
    "cancelledAt": "2026-01-01T00:00:00",
    "cancelReason": "string",
    "paymentRail": "string",
    "paymentIntentStatus": "string",
    "settlementReference": "string",
    "terminal": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/payment-link/{linkId}/complete

- Controller: `TransactionController.completePayment` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 230`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentLinkDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |
| `Idempotency-Key` | yes | `<String>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `linkId` | `string` | yes |

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
    "id": "string",
    "userId": 1,
    "sessionId": "string",
    "amountBtc": "0.00010000",
    "grossAmountBtc": "0.00010000",
    "depositFeeBtc": "0.00010000",
    "netAmountBtc": "0.00010000",
    "description": "string",
    "depositAddress": "string",
    "visibility": "string",
    "confirmationMode": "string",
    "amountLocked": true,
    "referenceLabel": "string",
    "metadata": {
      "key": "string"
    },
    "status": "string",
    "txid": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00",
    "completedAt": "2026-01-01T00:00:00",
    "cancelledAt": "2026-01-01T00:00:00",
    "cancelReason": "string",
    "paymentRail": "string",
    "paymentIntentStatus": "string",
    "settlementReference": "string",
    "terminal": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/payment-link/{linkId}/confirm

- Controller: `TransactionController.confirmPayment` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 208`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<PaymentLinkDTO>>`.

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
  "idempotencyKey": "string",
  "txid": "00000000-0000-0000-0000-000000000000",
  "fromAddress": "bc1qexampleaddress"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": "string",
    "userId": 1,
    "sessionId": "string",
    "amountBtc": "0.00010000",
    "grossAmountBtc": "0.00010000",
    "depositFeeBtc": "0.00010000",
    "netAmountBtc": "0.00010000",
    "description": "string",
    "depositAddress": "string",
    "visibility": "string",
    "confirmationMode": "string",
    "amountLocked": true,
    "referenceLabel": "string",
    "metadata": {
      "key": "string"
    },
    "status": "string",
    "txid": "string",
    "expiresAt": "2026-01-01T00:00:00",
    "createdAt": "2026-01-01T00:00:00",
    "paidAt": "2026-01-01T00:00:00",
    "completedAt": "2026-01-01T00:00:00",
    "cancelledAt": "2026-01-01T00:00:00",
    "cancelReason": "string",
    "paymentRail": "string",
    "paymentIntentStatus": "string",
    "settlementReference": "string",
    "terminal": true
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/payment-links

- Controller: `TransactionController.getUserPaymentLinks` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 275`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<PaymentLinkDTO>>>`.

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
      "id": "string",
      "userId": 1,
      "sessionId": "string",
      "amountBtc": "0.00010000",
      "grossAmountBtc": "0.00010000",
      "depositFeeBtc": "0.00010000",
      "netAmountBtc": "0.00010000",
      "description": "string",
      "depositAddress": "string",
      "visibility": "string",
      "confirmationMode": "string",
      "amountLocked": true,
      "referenceLabel": "string",
      "metadata": {
        "key": "string"
      },
      "status": "string",
      "txid": "string",
      "expiresAt": "2026-01-01T00:00:00",
      "createdAt": "2026-01-01T00:00:00",
      "paidAt": "2026-01-01T00:00:00",
      "completedAt": "2026-01-01T00:00:00",
      "cancelledAt": "2026-01-01T00:00:00",
      "cancelReason": "string",
      "paymentRail": "string",
      "paymentIntentStatus": "string",
      "settlementReference": "string",
      "terminal": true
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /transactions/status

- Controller: `TransactionController.getStatus` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 133`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<TransactionResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `txid` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "txid": "string",
    "status": "string",
    "feeSatoshis": 1,
    "amountReceived": "0.00010000",
    "sender": "string",
    "receiver": "string",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /transactions/withdraw

- Controller: `TransactionController.withdraw` (`backend/kerosene/src/main/java/source/transactions/controller/TransactionController.java: 291`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<TransactionResponseDTO>>`.

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
  "idempotencyKey": "string",
  "fromWalletName": "main",
  "toAddress": "bc1qexampleaddress",
  "amount": "0.00010000",
  "description": "string",
  "totpCode": "123456",
  "passkeyAssertionResponseJSON": "string",
  "passkeyAssertionRequestJSON": "string",
  "confirmationPassphrase": "correct-horse-battery-staple"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "txid": "string",
    "status": "string",
    "feeSatoshis": 1,
    "amountReceived": "0.00010000",
    "sender": "string",
    "receiver": "string",
    "context": "string"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Treasury

### GET /treasury/overview

- Controller: `TreasuryController.overview` (`backend/kerosene/src/main/java/source/treasury/controller/TreasuryController.java: 29`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<TreasuryOverviewDTO>`.

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
  "totalOnchainBtc": "0.00010000",
  "lightningNodeBtc": "0.00010000",
  "inboundLiquidityBtc": "0.00010000",
  "outboundLiquidityBtc": "0.00010000",
  "reservedOnchainBtc": "0.00010000",
  "reservedLightningBtc": "0.00010000",
  "availableOnchainBtc": "0.00010000",
  "availableLightningBtc": "0.00010000",
  "lightningSendsAllowed": true,
  "liquidityState": "string"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Payments

### GET /users/{receiverIdentifier}/receiving-capabilities

- Controller: `PaymentsController.receivingCapabilities` (`backend/kerosene/src/main/java/source/payments/controller/PaymentsController.java: 68`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<ReceivingCapabilitiesResponse>>`.

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
  "message": "...",
  "data": {
    "canReceiveInternal": true,
    "canReceiveLightning": true,
    "canReceiveOnchain": true,
    "preferredRail": "ACTIVE",
    "missingRequirements": [
      "string"
    ],
    "receiverDisplayName": "string",
    "availableRails": [
      "ACTIVE"
    ],
    "limits": {
      "asset": "string",
      "fiatCurrencies": [
        "string"
      ],
      "minInternalSats": 1,
      "minLightningSats": 1,
      "minOnchainSats": 1
    }
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Audit

### GET /v1/audit/config

- Controller: `LedgerAuditController.getTreasuryAuditConfig` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 112`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<?>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |
| `X-Admin-Token` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

Nenhum.

Response body:

```json
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### PUT /v1/audit/config

- Controller: `LedgerAuditController.updateTreasuryAuditConfig` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 124`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<?>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `X-Admin-Token` | no | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "maxWithdrawLimit": "0.00010000",
  "auditXpub": "string"
}
```

Response body:

```json
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /v1/audit/reserves/operational-proof

- Controller: `LedgerAuditController.generateOperationalReserveProof` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 139`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<OperationalReserveProofResponseDTO>`.

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
  "generatedAt": "2026-01-01T00:00:00Z",
  "status": "string",
  "solvent": true,
  "providersHealthy": true,
  "assets": {
    "hotWalletBtc": "0.00010000",
    "treasuryXpubOnchainBtc": "0.00010000",
    "lightningBtc": "0.00010000",
    "totalOnchainBtc": "0.00010000",
    "totalAssetsBtc": "0.00010000"
  },
  "liabilities": {
    "internalLedgerBtc": "0.00010000",
    "reservedOnchainBtc": "0.00010000",
    "reservedLightningBtc": "0.00010000",
    "totalOperationalExposureBtc": "0.00010000"
  },
  "chainState": {
    "bitcoinNetwork": "string",
    "bitcoinBlockHeight": 1,
    "bitcoinBestBlockHashRef": "string",
    "lightningBlockHeight": 1,
    "lightningBlockHashRef": "string"
  },
  "merkleProof": {
    "merkleRoot": "string",
    "ledgerCount": 1,
    "createdAt": "2026-01-01T00:00:00",
    "anchorTxidRef": "string"
  },
  "providers": [
    {
      "provider": "string",
      "status": "string",
      "source": "string",
      "message": "string"
    }
  ],
  "snapshotHash": "string",
  "panicReason": "string"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /v1/audit/siphon

- Controller: `LedgerAuditController.siphonFees` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 150`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<Map<String, String>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `X-Owner-TOTP` | yes | `<String>` |
| `X-Hardware-Signature` | yes | `<String>` |

Path params:

Nenhum.

Query params:

Nenhum.

Request body:

```json
{
  "idempotencyKey": "unique-key",
  "requestedBy": "operator",
  "amount": "0.00100000",
  "approvedBy": "operator",
  "reason": "manual operation"
}
```

Response body:

```json
{
  "key": "string"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /v1/audit/siphon/requests

- Controller: `LedgerAuditController.requestSiphonPayout` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 181`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<?>`.

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
  "idempotencyKey": "unique-key",
  "requestedBy": "operator",
  "amount": "0.00100000",
  "approvedBy": "operator",
  "reason": "manual operation"
}
```

Response body:

```json
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /v1/audit/siphon/requests/{requestId}/approve

- Controller: `LedgerAuditController.approveSiphonPayout` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 195`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<?>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt com ROLE_ADMIN>` |
| `Content-Type` | yes | `application/json` |
| `Digest` | no | `SHA-256=<base64 do corpo>; se enviado, precisa bater com o corpo` |
| `X-Owner-TOTP` | yes | `<String>` |
| `X-Hardware-Signature` | yes | `<String>` |

Path params:

| Param | Tipo | Obrigatorio |
| --- | --- | --- |
| `requestId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "idempotencyKey": "unique-key",
  "requestedBy": "operator",
  "amount": "0.00100000",
  "approvedBy": "operator",
  "reason": "manual operation"
}
```

Response body:

```json
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /v1/audit/siphon/requests/{requestId}/cancel

- Controller: `LedgerAuditController.cancelSiphonPayout` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 217`).
- Autenticacao: `JWT com ROLE_ADMIN`.
- Response Java: `ResponseEntity<?>`.

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
| `requestId` | `string` | yes |

Query params:

Nenhum.

Request body:

```json
{
  "idempotencyKey": "unique-key",
  "requestedBy": "operator",
  "amount": "0.00100000",
  "approvedBy": "operator",
  "reason": "manual operation"
}
```

Response body:

```json
"string"
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### GET /v1/audit/stats

- Controller: `LedgerAuditController.getTransparencyStats` (`backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java: 78`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<Map<String, Object>>`.

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
  "liability_to_users": "0.00010000",
  "platform_profit_pending": "0.00000100",
  "actual_onchain_balance": "0.00020000",
  "actual_lightning_balance": "0.00000000",
  "actual_wallet_xpub_balance": "0.00010000",
  "actual_treasury_xpub_balance": "0.00010000",
  "actual_total_assets": "0.00020000",
  "is_solvent": true
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

## Wallet

### GET /wallet/all

- Controller: `WalletController.getAllWallets` (`backend/kerosene/src/main/java/source/wallet/controller/WalletController.java: 41`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<List<WalletResponseDTO>>>`.

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
      "name": "string",
      "createdAt": "2026-01-01T00:00:00",
      "updatedAt": "2026-01-01T00:00:00",
      "isActive": true,
      "totpUri": "string",
      "depositAddress": "string",
      "lightningAddress": "string",
      "walletMode": "string",
      "xpubConfigured": true,
      "cardType": "string",
      "cardHolderName": "string",
      "cardMaskedNumber": "string",
      "cardNumberSuffix": "string",
      "cardSequence": 1,
      "cardRotationStatus": "string",
      "cardIssuedAt": "2026-01-01T00:00:00",
      "cardExpiresAt": "2026-01-01T00:00:00",
      "cardNextRotationAt": "2026-01-01T00:00:00",
      "cardLastRotatedAt": "2026-01-01T00:00:00",
      "previousCardNumberSuffix": "string",
      "previousCardExpiresAt": "2026-01-01T00:00:00",
      "withdrawalFeeRate": "0.00010000",
      "depositFeeRate": "0.00010000"
    }
  ],
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### POST /wallet/create

- Controller: `WalletController.create` (`backend/kerosene/src/main/java/source/wallet/controller/WalletController.java: 32`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<WalletResponseDTO>>`.

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
  "passphrase": "correct-horse-battery-staple",
  "name": "string",
  "xpub": "string",
  "walletMode": "string"
}
```

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": 1,
    "name": "string",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "isActive": true,
    "totpUri": "string",
    "depositAddress": "string",
    "lightningAddress": "string",
    "walletMode": "string",
    "xpubConfigured": true,
    "cardType": "string",
    "cardHolderName": "string",
    "cardMaskedNumber": "string",
    "cardNumberSuffix": "string",
    "cardSequence": 1,
    "cardRotationStatus": "string",
    "cardIssuedAt": "2026-01-01T00:00:00",
    "cardExpiresAt": "2026-01-01T00:00:00",
    "cardNextRotationAt": "2026-01-01T00:00:00",
    "cardLastRotatedAt": "2026-01-01T00:00:00",
    "previousCardNumberSuffix": "string",
    "previousCardExpiresAt": "2026-01-01T00:00:00",
    "withdrawalFeeRate": "0.00010000",
    "depositFeeRate": "0.00010000"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### DELETE /wallet/delete

- Controller: `WalletController.deleteWallets` (`backend/kerosene/src/main/java/source/wallet/controller/WalletController.java: 63`).
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
  "passphrase": "correct-horse-battery-staple",
  "name": "string",
  "xpub": "string",
  "walletMode": "string"
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

### GET /wallet/find

- Controller: `WalletController.getWalletByName` (`backend/kerosene/src/main/java/source/wallet/controller/WalletController.java: 48`).
- Autenticacao: `JWT`.
- Response Java: `ResponseEntity<ApiResponse<WalletResponseDTO>>`.

Headers:

| Header | Obrigatorio | Valor/observacao |
| --- | --- | --- |
| `Accept` | no | `application/json` |
| `Authorization` | yes | `Bearer <jwt>` |

Path params:

Nenhum.

Query params:

| Param | Tipo | Obrigatorio | Default |
| --- | --- | --- | --- |
| `name` | `String` | true | `` |

Request body:

Nenhum.

Response body:

```json
{
  "success": true,
  "message": "...",
  "data": {
    "id": 1,
    "name": "string",
    "createdAt": "2026-01-01T00:00:00",
    "updatedAt": "2026-01-01T00:00:00",
    "isActive": true,
    "totpUri": "string",
    "depositAddress": "string",
    "lightningAddress": "string",
    "walletMode": "string",
    "xpubConfigured": true,
    "cardType": "string",
    "cardHolderName": "string",
    "cardMaskedNumber": "string",
    "cardNumberSuffix": "string",
    "cardSequence": 1,
    "cardRotationStatus": "string",
    "cardIssuedAt": "2026-01-01T00:00:00",
    "cardExpiresAt": "2026-01-01T00:00:00",
    "cardNextRotationAt": "2026-01-01T00:00:00",
    "cardLastRotatedAt": "2026-01-01T00:00:00",
    "previousCardNumberSuffix": "string",
    "previousCardExpiresAt": "2026-01-01T00:00:00",
    "withdrawalFeeRate": "0.00010000",
    "depositFeeRate": "0.00010000"
  },
  "timestamp": "2026-01-01T00:00:00"
}
```

Erros relevantes: `400` validacao/desserializacao, `401` token ausente/invalido quando protegido, `403` autorizacao insuficiente, `404` recurso inexistente, `409` conflito de estado, `413` payload acima do limite, `415` content-type invalido, `429` rate limit, `500` erro nao tratado. Consulte o controller e `GlobalExceptionHandler` para codigos de dominio especificos.

### PUT /wallet/update

- Controller: `WalletController.updateWallet` (`backend/kerosene/src/main/java/source/wallet/controller/WalletController.java: 56`).
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
  "passphrase": "correct-horse-battery-staple",
  "name": "string",
  "newName": "string",
  "newXpub": "string",
  "newWalletMode": "string"
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

Source: `backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java`

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

### BroadcastTransactionDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/BroadcastTransactionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `rawTxHex` | `String` | `-` |
| `toAddress` | `String` | `-` |
| `amount` | `java.math.BigDecimal` | `-` |
| `message` | `String` | `-` |

### CancelPaymentLinkRequest

Source: `backend/kerosene/src/main/java/source/transactions/dto/CancelPaymentLinkRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `reason` | `String` | `-` |

### ChainState

Source: `backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `bitcoinNetwork` | `String` | `-` |
| `bitcoinBlockHeight` | `Long` | `-` |
| `bitcoinBestBlockHashRef` | `String` | `-` |
| `lightningBlockHeight` | `Long` | `-` |
| `lightningBlockHashRef` | `String` | `-` |

### ClassifyTaxEventRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `classification` | `String` | `@NotBlank(message = "classification is required")` |

### ConfigureAppPinRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/ConfigureAppPinRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `enabled` | `Boolean` | `-` |
| `pin` | `String` | `-` |
| `currentPin` | `String` | `-` |
| `totpCode` | `String` | `-` |

### ConfirmPaymentRequest

Source: `backend/kerosene/src/main/java/source/transactions/dto/ConfirmPaymentRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters")` |
| `txid` | `String` | `@NotBlank(message = "txid is required")` |
| `fromAddress` | `String` | `-` |

### CreateColdWalletRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `label` | `String` | `-` |
| `descriptor` | `String` | `-` |
| `xpub` | `String` | `-` |
| `fingerprint` | `String` | `@NotBlank(message = "fingerprint is required")` |
| `derivationPath` | `String` | `@NotBlank(message = "derivationPath is required")` |
| `scriptPolicy` | `String` | `-` |

### CreateInternalCardRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `label` | `String` | `-` |
| `riskTier` | `String` | `-` |

### CreatePaymentLinkRequest

Source: `backend/kerosene/src/main/java/source/transactions/dto/CreatePaymentLinkRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `description` | `String` | `-` |
| `expiresInMinutes` | `Integer` | `-` |
| `visibility` | `String` | `-` |
| `confirmationMode` | `String` | `-` |
| `amountLocked` | `Boolean` | `-` |
| `referenceLabel` | `String` | `-` |
| `metadata` | `Map<String, String>` | `-` |

### CreatePaymentRequestReq

Source: `backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `receiverWalletName` | `String` | `@NotBlank(message = "receiverWalletName is required")` |

### CreatePsbtRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `destinationAddress` | `String` | `@NotBlank(message = "destinationAddress is required")` |
| `amountSats` | `long` | `@Positive(message = "amountSats must be positive")` |
| `feeRate` | `Long` | `-` |
| `selectedUtxoIds` | `List<UUID>` | `-` |

### CreateReceiveRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `amountSats` | `Long` | `@Positive(message = "amountSats must be positive when provided")` |
| `expiry` | `String` | `-` |
| `oneTime` | `Boolean` | `-` |

### DepositConfirmRequest

Source: `backend/kerosene/src/main/java/source/transactions/dto/DepositConfirmRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `txid` | `String` | `-` |
| `fromAddress` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |

### DepositDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/DepositDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `Long` | `-` |
| `userId` | `Long` | `-` |
| `txid` | `String` | `-` |
| `fromAddress` | `String` | `-` |
| `toAddress` | `String` | `-` |
| `amountBtc` | `BigDecimal` | `-` |
| `confirmations` | `Long` | `-` |
| `status` | `String` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `confirmedAt` | `LocalDateTime` | `-` |

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

### EstimatedFeeDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/EstimatedFeeDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `fastSatoshisPerByte` | `Long` | `-` |
| `standardSatoshisPerByte` | `Long` | `-` |
| `slowSatoshisPerByte` | `Long` | `-` |
| `estimatedFastBtc` | `BigDecimal` | `-` |
| `estimatedStandardBtc` | `BigDecimal` | `-` |
| `estimatedSlowBtc` | `BigDecimal` | `-` |
| `amountReceived` | `BigDecimal` | `-` |
| `totalToSend` | `BigDecimal` | `-` |

### ExternalTransferResponseDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/ExternalTransferResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `UUID` | `-` |
| `network` | `String` | `-` |
| `transferType` | `String` | `-` |
| `status` | `String` | `-` |
| `provider` | `String` | `-` |
| `walletName` | `String` | `-` |
| `destination` | `String` | `-` |
| `invoiceId` | `String` | `-` |
| `blockchainTxid` | `String` | `-` |
| `paymentHash` | `String` | `-` |
| `invoiceData` | `String` | `-` |
| `expectedAmountBtc` | `BigDecimal` | `-` |
| `amountBtc` | `BigDecimal` | `-` |
| `networkFeeBtc` | `BigDecimal` | `-` |
| `platformFeeBtc` | `BigDecimal` | `-` |
| `totalDebitedBtc` | `BigDecimal` | `-` |
| `externalReference` | `String` | `-` |
| `confirmations` | `Integer` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |
| `detectedAt` | `LocalDateTime` | `-` |
| `settledAt` | `LocalDateTime` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `updatedAt` | `LocalDateTime` | `-` |
| `context` | `String` | `-` |

### InternalPaymentRequestDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/InternalPaymentRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `String` | `-` |
| `requesterUserId` | `Long` | `-` |
| `receiverWalletId` | `Long` | `-` |
| `receiverWalletName` | `String` | `-` |
| `destinationHash` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |
| `status` | `String` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `paidAt` | `LocalDateTime` | `-` |

### InternalTransactionResponseDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/InternalTransactionResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `txid` | `String` | `-` |
| `status` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |
| `sender` | `String` | `-` |
| `receiver` | `String` | `-` |
| `context` | `String` | `-` |

### LedgerDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/LedgerDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `Integer` | `-` |
| `walletId` | `Long` | `-` |
| `walletName` | `String` | `-` |
| `balance` | `BigDecimal` | `-` |
| `nonce` | `Integer` | `-` |
| `lastHash` | `String` | `-` |
| `context` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |

### LedgerSyncEventDTO

Source: `backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `UUID` | `-` |
| `transactionType` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |
| `status` | `String` | `-` |
| `networkFee` | `BigDecimal` | `-` |
| `txidFingerprint` | `String` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `confirmations` | `Integer` | `-` |

### Liabilities

Source: `backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `internalLedgerBtc` | `BigDecimal` | `-` |
| `reservedOnchainBtc` | `BigDecimal` | `-` |
| `reservedLightningBtc` | `BigDecimal` | `-` |
| `totalOperationalExposureBtc` | `BigDecimal` | `-` |

### LightningInvoiceRequestDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/LightningInvoiceRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters")` |
| `walletName` | `String` | `@NotBlank(message = "walletName is required")` |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `memo` | `String` | `-` |
| `expiresInSeconds` | `Integer` | `-` |

### LightningInvoiceResponseDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/LightningInvoiceResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `transferId` | `UUID` | `-` |
| `walletName` | `String` | `-` |
| `paymentRequest` | `String` | `-` |
| `paymentHash` | `String` | `-` |
| `lightningAddress` | `String` | `-` |
| `amountBtc` | `BigDecimal` | `-` |
| `provider` | `String` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |
| `status` | `String` | `-` |

### LightningPaymentRequestDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/LightningPaymentRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters")` |
| `fromWalletName` | `String` | `@NotBlank(message = "fromWalletName is required")` |
| `paymentRequest` | `String` | `@NotBlank(message = "paymentRequest is required")` |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `maxRoutingFeeBtc` | `BigDecimal` | `-` |
| `description` | `String` | `-` |
| `totpCode` | `String` | `-` |
| `passkeyAssertionResponseJSON` | `String` | `-` |
| `confirmationPassphrase` | `String` | `-` |

### Limits

Source: `backend/kerosene/src/main/java/source/payments/dto/ReceivingCapabilitiesResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `asset` | `String` | `-` |
| `fiatCurrencies` | `List<String>` | `-` |
| `minInternalSats` | `long` | `-` |
| `minLightningSats` | `long` | `-` |
| `minOnchainSats` | `long` | `-` |

### MerkleProof

Source: `backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java`

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

### OnchainAddressAllocationDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/OnchainAddressAllocationDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `walletName` | `String` | `-` |
| `onchainAddress` | `String` | `-` |
| `expectedAmountBtc` | `BigDecimal` | `-` |
| `network` | `String` | `-` |
| `provider` | `String` | `-` |
| `externalWalletReference` | `String` | `-` |
| `walletMode` | `String` | `-` |
| `transferId` | `UUID` | `-` |
| `transferStatus` | `String` | `-` |
| `confirmations` | `Integer` | `-` |
| `requiredConfirmations` | `Integer` | `-` |
| `blockchainTxid` | `String` | `-` |

### OnchainAddressRequestDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/OnchainAddressRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `walletName` | `String` | `-` |
| `expectedAmountBtc` | `BigDecimal` | `-` |

### OnchainSendRequestDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/OnchainSendRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters")` |
| `fromWalletName` | `String` | `@NotBlank(message = "fromWalletName is required")` |
| `toAddress` | `String` | `@NotBlank(message = "toAddress is required")` |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `description` | `String` | `-` |
| `totpCode` | `String` | `-` |
| `passkeyAssertionResponseJSON` | `String` | `-` |
| `confirmationPassphrase` | `String` | `-` |

### OperationalReserveProofResponseDTO

Source: `backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `generatedAt` | `Instant` | `-` |
| `status` | `String` | `-` |
| `solvent` | `boolean` | `-` |
| `providersHealthy` | `boolean` | `-` |
| `assets` | `Assets` | `-` |
| `liabilities` | `Liabilities` | `-` |
| `chainState` | `ChainState` | `-` |
| `merkleProof` | `MerkleProof` | `-` |
| `providers` | `List<ProviderHealth>` | `-` |
| `snapshotHash` | `String` | `-` |
| `panicReason` | `String` | `-` |

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

### PayPaymentRequestReq

Source: `backend/kerosene/src/main/java/source/ledger/controller/LedgerController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required")` |
| `payerWalletName` | `String` | `-` |
| `totpCode` | `String` | `-` |
| `passkeyAssertionJson` | `String` | `-` |
| `confirmationPassphrase` | `String` | `-` |

### PaymentConfirmRequest

Source: `backend/kerosene/src/main/java/source/payments/dto/PaymentConfirmRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank @Size(max = 128)` |
| `userConfirmationToken` | `String` | `@Size(max = 512)` |
| `acceptedTotalDebitSats` | `Long` | `@NotNull` |
| `acceptedReceiverAmountSats` | `Long` | `@NotNull` |

### PaymentLinkDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/PaymentLinkDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `String` | `-` |
| `userId` | `Long` | `-` |
| `sessionId` | `String` | `-` |
| `amountBtc` | `BigDecimal` | `-` |
| `grossAmountBtc` | `BigDecimal` | `-` |
| `depositFeeBtc` | `BigDecimal` | `-` |
| `netAmountBtc` | `BigDecimal` | `-` |
| `description` | `String` | `-` |
| `depositAddress` | `String` | `-` |
| `visibility` | `String` | `-` |
| `confirmationMode` | `String` | `-` |
| `amountLocked` | `Boolean` | `-` |
| `referenceLabel` | `String` | `-` |
| `metadata` | `Map<String, String>` | `-` |
| `status` | `String` | `-` |
| `txid` | `String` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `paidAt` | `LocalDateTime` | `-` |
| `completedAt` | `LocalDateTime` | `-` |
| `cancelledAt` | `LocalDateTime` | `-` |
| `cancelReason` | `String` | `-` |
| `paymentRail` | `String` | `-` |
| `paymentIntentStatus` | `String` | `-` |
| `settlementReference` | `String` | `-` |
| `terminal` | `Boolean` | `-` |

### PaymentQuoteRequest

Source: `backend/kerosene/src/main/java/source/payments/dto/PaymentQuoteRequest.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `rail` | `PaymentEnums.PaymentRail` | `@NotNull` |
| `feeMode` | `PaymentEnums.FeeMode` | `@NotNull` |
| `amountFiat` | `String` | `@NotBlank @Size(max = 40)` |
| `fiatCurrency` | `String` | `@NotBlank @Size(max = 8)` |
| `asset` | `String` | `@NotBlank @Size(max = 16)` |
| `receiverIdentifier` | `String` | `@Size(max = 255)` |
| `externalDestination` | `String` | `@Size(max = 2048)` |
| `speed` | `PaymentEnums.OnchainSpeed` | `-` |

### PaymentQuoteResponse

Source: `backend/kerosene/src/main/java/source/payments/dto/PaymentQuoteResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `paymentIntentId` | `UUID` | `-` |
| `quoteExpiresAt` | `Instant` | `-` |
| `rail` | `PaymentEnums.PaymentRail` | `-` |
| `feeMode` | `PaymentEnums.FeeMode` | `-` |
| `receiverDisplayName` | `String` | `-` |
| `receiverAmountFiat` | `String` | `-` |
| `receiverAmountSats` | `long` | `-` |
| `totalDebitFiat` | `String` | `-` |
| `totalDebitSats` | `long` | `-` |
| `networkFeeFiat` | `String` | `-` |
| `networkFeeSats` | `long` | `-` |
| `keroseneFeeFiat` | `String` | `-` |
| `keroseneFeeSats` | `long` | `-` |
| `warnings` | `List<String>` | `-` |
| `requiresConfirmation` | `boolean` | `-` |

### PaymentRequestPublicDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/PaymentRequestPublicDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |
| `status` | `String` | `-` |
| `expiresAt` | `LocalDateTime` | `-` |
| `destinationHash` | `String` | `-` |
| `locked` | `boolean` | `-` |

### PaymentStatusResponse

Source: `backend/kerosene/src/main/java/source/payments/dto/PaymentStatusResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `paymentIntentId` | `UUID` | `-` |
| `status` | `PaymentEnums.PaymentIntentStatus` | `-` |
| `rail` | `PaymentEnums.PaymentRail` | `-` |
| `feeMode` | `PaymentEnums.FeeMode` | `-` |
| `receiverDisplayName` | `String` | `-` |
| `receiverAmountSats` | `long` | `-` |
| `totalDebitSats` | `long` | `-` |
| `networkFeeSats` | `long` | `-` |
| `keroseneFeeSats` | `long` | `-` |
| `quoteExpiresAt` | `Instant` | `-` |
| `failureCode` | `String` | `-` |
| `failureMessage` | `String` | `-` |
| `warnings` | `List<String>` | `-` |

### ProviderHealth

Source: `backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `provider` | `String` | `-` |
| `status` | `String` | `-` |
| `source` | `String` | `-` |
| `message` | `String` | `-` |

### ReceivingCapabilitiesResponse

Source: `backend/kerosene/src/main/java/source/payments/dto/ReceivingCapabilitiesResponse.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `canReceiveInternal` | `boolean` | `-` |
| `canReceiveLightning` | `boolean` | `-` |
| `canReceiveOnchain` | `boolean` | `-` |
| `preferredRail` | `PaymentEnums.PaymentRail` | `-` |
| `missingRequirements` | `List<String>` | `-` |
| `receiverDisplayName` | `String` | `-` |
| `availableRails` | `List<PaymentEnums.PaymentRail>` | `-` |
| `limits` | `Limits` | `-` |

### ResponseError

Source: `backend/kerosene/src/main/java/source/auth/dto/ResponseError.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `timestamp` | `LocalDateTime` | `-` |
| `status` | `HttpStatus` | `-` |
| `error` | `String` | `-` |
| `message` | `String` | `-` |
| `path` | `String` | `-` |

### SignedTransactionDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/SignedTransactionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `rawTxHex` | `String` | `-` |
| `description` | `String` | `-` |

### SignupResponseDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/SignupResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `sessionId` | `String` | `-` |
| `otpUri` | `String` | `-` |
| `backupCodes` | `List<String>` | `-` |
| `totpOptional` | `boolean` | `-` |

### SubmitSignedPsbtRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `signedPsbt` | `String` | `@NotBlank(message = "signedPsbt is required")` |
| `broadcast` | `Boolean` | `@NotNull(message = "broadcast is required")` |

### TotpSetupResponseDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/TotpSetupResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `otpUri` | `String` | `-` |
| `secret` | `String` | `-` |

### TransactionDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/TransactionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `sender` | `String` | `@NotBlank(message = "sender is required")` |
| `receiver` | `String` | `@NotBlank(message = "receiver is required")` |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `context` | `String` | `-` |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters")` |
| `requestTimestamp` | `Long` | `-` |
| `passkeyAssertionJson` | `String` | `-` |
| `confirmationPassphrase` | `String` | `-` |
| `totpCode` | `String` | `-` |

### TransactionInput

Source: `backend/kerosene/src/main/java/source/transactions/dto/UnsignedTransactionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `txid` | `String` | `-` |
| `vout` | `Integer` | `-` |
| `value` | `BigDecimal` | `-` |
| `scriptPubKey` | `String` | `-` |

### TransactionOutput

Source: `backend/kerosene/src/main/java/source/transactions/dto/UnsignedTransactionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `address` | `String` | `-` |
| `value` | `BigDecimal` | `-` |

### TransactionRequestDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/TransactionRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `fromAddress` | `String` | `-` |
| `toAddress` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |
| `feeSatoshis` | `Long` | `-` |

### TransactionResponseDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/TransactionResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `txid` | `String` | `-` |
| `status` | `String` | `-` |
| `feeSatoshis` | `Long` | `-` |
| `amountReceived` | `BigDecimal` | `-` |
| `sender` | `String` | `-` |
| `receiver` | `String` | `-` |
| `context` | `String` | `-` |

### TreasuryAuditConfigRequestDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/TreasuryAuditConfigRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `maxWithdrawLimit` | `BigDecimal` | `-` |
| `auditXpub` | `String` | `-` |

### TreasuryAuditConfigResponseDTO

Source: `backend/kerosene/src/main/java/source/ledger/dto/TreasuryAuditConfigResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `maxWithdrawLimit` | `BigDecimal` | `-` |
| `auditXpubConfigured` | `boolean` | `-` |
| `auditXpubPreview` | `String` | `-` |
| `updatedAt` | `LocalDateTime` | `-` |

### TreasuryOverviewDTO

Source: `backend/kerosene/src/main/java/source/treasury/dto/TreasuryOverviewDTO.java`

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

### TreasuryPayoutResponseDTO

Source: `backend/kerosene/src/main/java/source/treasury/dto/TreasuryPayoutResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `UUID` | `-` |
| `status` | `String` | `-` |
| `amount` | `BigDecimal` | `-` |
| `destinationAddress` | `String` | `-` |
| `idempotencyKey` | `String` | `-` |
| `requestedAt` | `LocalDateTime` | `-` |
| `executableAfter` | `LocalDateTime` | `-` |
| `approvedAt` | `LocalDateTime` | `-` |
| `queuedAt` | `LocalDateTime` | `-` |
| `executedAt` | `LocalDateTime` | `-` |
| `providerReference` | `String` | `-` |
| `blockchainTxid` | `String` | `-` |
| `providerStatus` | `String` | `-` |
| `attempts` | `int` | `-` |
| `retryable` | `boolean` | `-` |
| `lastError` | `String` | `-` |

### UnsignedTransactionDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/UnsignedTransactionDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `rawTxHex` | `String` | `-` |
| `txId` | `String` | `-` |
| `inputs` | `List<TransactionInput>` | `-` |
| `outputs` | `List<TransactionOutput>` | `-` |
| `totalAmount` | `BigDecimal` | `-` |
| `fee` | `Long` | `-` |
| `fromAddress` | `String` | `-` |
| `toAddress` | `String` | `-` |
| `txid` | `String` | `-` |
| `vout` | `Integer` | `-` |
| `value` | `BigDecimal` | `-` |
| `scriptPubKey` | `String` | `-` |
| `address` | `String` | `-` |
| `value` | `BigDecimal` | `-` |

### UserActionRequest

Source: `backend/kerosene/src/main/java/source/bitcoinaccounts/controller/BitcoinAccountsController.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `action` | `String` | `@NotBlank(message = "action is required")` |

### VerifyAppPinRequestDTO

Source: `backend/kerosene/src/main/java/source/auth/dto/VerifyAppPinRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `pin` | `String` | `-` |

### WalletNetworkAddressDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/WalletNetworkAddressDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `walletName` | `String` | `-` |
| `onchainAddress` | `String` | `-` |
| `lightningAddress` | `String` | `-` |
| `network` | `String` | `-` |
| `provider` | `String` | `-` |
| `externalWalletReference` | `String` | `-` |
| `walletMode` | `String` | `-` |
| `lightningEnabled` | `boolean` | `-` |
| `lightningUnavailableReason` | `String` | `-` |

### WalletRequestDTO

Source: `backend/kerosene/src/main/java/source/wallet/dto/WalletRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `passphrase` | `String` | `@NotBlank(message = "A passphrase e obrigatoria")` |
| `name` | `String` | `@NotBlank(message = "O nome da carteira e obrigatorio") @Size(min = 3, max = 50, message = "O nome deve ter entre 3 e 50 caracteres")` |
| `xpub` | `String` | `-` |
| `walletMode` | `String` | `-` |

### WalletResponseDTO

Source: `backend/kerosene/src/main/java/source/wallet/dto/WalletResponseDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `id` | `Long` | `-` |
| `name` | `String` | `-` |
| `createdAt` | `LocalDateTime` | `-` |
| `updatedAt` | `LocalDateTime` | `-` |
| `isActive` | `Boolean` | `-` |
| `totpUri` | `String` | `-` |
| `depositAddress` | `String` | `-` |
| `lightningAddress` | `String` | `-` |
| `walletMode` | `String` | `-` |
| `xpubConfigured` | `Boolean` | `-` |
| `cardType` | `String` | `-` |
| `cardHolderName` | `String` | `-` |
| `cardMaskedNumber` | `String` | `-` |
| `cardNumberSuffix` | `String` | `-` |
| `cardSequence` | `Integer` | `-` |
| `cardRotationStatus` | `String` | `-` |
| `cardIssuedAt` | `LocalDateTime` | `-` |
| `cardExpiresAt` | `LocalDateTime` | `-` |
| `cardNextRotationAt` | `LocalDateTime` | `-` |
| `cardLastRotatedAt` | `LocalDateTime` | `-` |
| `previousCardNumberSuffix` | `String` | `-` |
| `previousCardExpiresAt` | `LocalDateTime` | `-` |
| `withdrawalFeeRate` | `BigDecimal` | `-` |
| `depositFeeRate` | `BigDecimal` | `-` |

### WalletUpdateDTO

Source: `backend/kerosene/src/main/java/source/wallet/dto/WalletUpdateDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `passphrase` | `String` | `@NotBlank(message = "A passphrase e obrigatoria para autorizar a modificacao")` |
| `name` | `String` | `@NotBlank(message = "O nome atual da carteira e obrigatorio")` |
| `newName` | `String` | `-` |
| `newXpub` | `String` | `-` |
| `newWalletMode` | `String` | `-` |

### WithdrawRequestDTO

Source: `backend/kerosene/src/main/java/source/transactions/dto/WithdrawRequestDTO.java`

| Campo | Tipo | Validacao observada |
| --- | --- | --- |
| `idempotencyKey` | `String` | `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters")` |
| `fromWalletName` | `String` | `@NotBlank(message = "fromWalletName is required")` |
| `toAddress` | `String` | `@NotBlank(message = "toAddress is required")` |
| `amount` | `BigDecimal` | `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")` |
| `description` | `String` | `-` |
| `totpCode` | `String` | `-` |
| `passkeyAssertionResponseJSON` | `String` | `-` |
| `passkeyAssertionRequestJSON` | `String` | `-` |
| `confirmationPassphrase` | `String` | `-` |
