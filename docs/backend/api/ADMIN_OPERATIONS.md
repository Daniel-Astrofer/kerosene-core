# Admin Operations API

> Fonte de verdade desta página: controllers, DTOs, records, enums e `EndpointPolicyRegistry` do backend Spring Boot em `backend/kerosene/src/main/java`.
> Quando uma rota existe no controller mas não está declarada no `EndpointPolicyRegistry`, ela é documentada como `DENIED_BY_DEFAULT`, porque `Security.anyRequest().denyAll()` bloqueia a chamada antes do método executar.

## Convenções globais

### Envelope padrão `ApiResponse<T>`

A maior parte dos endpoints retorna o envelope abaixo. Endpoints explicitamente marcados como `raw` retornam o payload diretamente sem envelope. Campos `null` podem ser omitidos por `@JsonInclude`.

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---:|:---:|---|---|
| `success` | boolean | não | Indica sucesso lógico da operação. | `true` |
| `message` | string | sim | Mensagem operacional retornada pelo controller/service. | `KFE wallet created.` |
| `data` | object/array/string | sim | Payload específico do endpoint. | `{...}` |
| `errorCode` | string | sim | Código de erro de negócio/validação quando `success=false`. | `AUTH_INVALID_CREDENTIALS` |
| `timestamp` | string date-time | não | Momento de montagem do envelope no servidor. | `2026-06-19T10:30:00` |

### Headers comuns

| Header | Tipo | Obrigatório | Quando usar | Exemplo |
|---|---:|:---:|---|---|
| `Content-Type` | string | sim em requests com body | Deve ser `application/json` para JSON. | `application/json` |
| `Authorization` | string | sim para `AUTHENTICATED`/`ADMIN` | Bearer token JWT emitido por login/TOTP/passkey/device-key. | `Bearer eyJhbGciOi...` |
| `X-Correlation-Id` | string | não | Correlação distribuída entre serviços/logs. Aceita caracteres seguros de 8 a 64 posições. | `req-20260619-0001` |
| `X-Request-Id` | string | não | Identificador idempotente/observável da requisição. | `mobile-01HD...` |
| `X-Device-Hash` | string | condicional | Usado por endpoints device-scoped de segurança/PIN. | `sha256:abc123...` |
| `X-Idempotency-Key` | string | recomendado | Recomendado em operações financeiras, embora KFE use `idempotencyKey` no body. | `txn-01J...` |

### Classes de autenticação

| Valor na documentação | Significado efetivo |
|---|---|
| `PUBLIC` | Permitido sem JWT. |
| `AUTHENTICATED` | Exige JWT válido. |
| `ADMIN` | Exige JWT com role `ADMIN`; alguns métodos também usam `@PreAuthorize`. |
| `DENIED_BY_DEFAULT` | Controller/DTO pode existir, mas a rota não tem policy e é bloqueada por `anyRequest().denyAll()`. |
| `STALE` | Documento legado sem controller ativo confirmado no código atual. |

### Estruturas de erro

Erro via envelope `ApiResponse`:

```json
{
  "success": false,
  "message": "Invalid token context",
  "data": null,
  "errorCode": "AUTH_SESSION_EXPIRED",
  "timestamp": "2026-06-19T10:30:00"
}
```

Erro MVC/validação ou fallback pode aparecer como `ResponseError`/erro Spring, dependendo do ponto da falha:

```json
{
  "timestamp": "2026-06-19T10:30:00",
  "status": "BAD_REQUEST",
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/auth/signup"
}
```

### Status codes comuns

| Status | Quando ocorre | Como resolver |
|---:|---|---|
| `200 OK` | Consulta ou operação concluída. | Consumir `data`. |
| `201 Created` | Recurso criado. | Persistir o `id` retornado. |
| `202 Accepted` | Fluxo assíncrono/pendente, comum em login com TOTP/aprovação. | Continuar no próximo passo indicado. |
| `204 No Content` | Operação concluída sem corpo. | Não tentar parsear JSON. |
| `400 Bad Request` | JSON inválido, campo faltante, enum inválido ou regra básica violada. | Corrigir request body/query/path. |
| `401 Unauthorized` | JWT ausente/inválido/expirado ou credencial incorreta. | Fazer login/renovar token ou reenviar credenciais. |
| `403 Forbidden` | Usuário autenticado sem permissão, admin token inválido ou policy nega acesso. | Verificar role/headers/policy. |
| `404 Not Found` | Recurso, sessão, dispositivo ou endpoint não encontrado. | Verificar identificadores. |
| `409 Conflict` | Duplicidade/idempotência/conflito de estado. | Consultar recurso existente ou trocar chave idempotente. |
| `410 Gone` | Sessão de recuperação expirada. | Reiniciar o fluxo. |
| `412 Precondition Failed` | Pré-condição criptográfica/security step-up falhou. | Refazer challenge/assinatura/fator. |
| `422 Unprocessable Entity` | Regra de negócio não satisfeita. | Ajustar estado da conta/recurso antes de repetir. |
| `429 Too Many Requests` | Rate limit. | Aplicar backoff exponencial. |
| `500 Internal Server Error` | Falha não tratada. | Registrar `X-Correlation-Id` e abrir incidente. |
| `503 Service Unavailable` | Dependência indisponível ou health `DOWN`. | Tentar novamente ou acionar operação. |

## Visão geral do serviço

API operacional para painéis administrativos, observabilidade e distribuição de release. Ela combina endpoints admin protegidos por role `ADMIN` e endpoints públicos de release/download.





## Endpoints

## Operations overview

**Método e URL:** `GET /api/admin/operations/overview`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Agrega visão executiva da operação: health, release, mobile, blockchain, lightning, Vault Raft, logs e métricas.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/overview' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Operational health

**Método e URL:** `GET /api/admin/operations/health`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna snapshot operacional consolidado das dependências.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/health' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Blockchain status

**Método e URL:** `GET /api/admin/operations/blockchain`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna estado operacional do nó Bitcoin/indexador quando configurado.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/blockchain' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Lightning status

**Método e URL:** `GET /api/admin/operations/lightning`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna estado do provedor Lightning/LND quando configurado.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/lightning' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Vault Raft status

**Método e URL:** `GET /api/admin/operations/vault-raft`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna snapshot do cluster Vault Raft.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/vault-raft' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Release status

**Método e URL:** `GET /api/admin/operations/release`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna release manifest/snapshot atualmente publicado.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/release' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Mobile release

**Método e URL:** `GET /api/admin/operations/mobile`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna informações de release/download mobile.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/mobile' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Operational metrics

**Método e URL:** `GET /api/admin/operations/metrics`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Retorna métricas técnicas agregadas.

**Quando usar:** Use em dashboards administrativos e runbooks de operação.

**Regras de negócio e limitações:** Exige role ADMIN pela policy `/api/admin/**` e também por `@PreAuthorize` no controller.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/metrics' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | Mapa/snapshot específico do domínio operacional. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Operation retrieved.",
  "data": {
    "status": "UP",
    "generatedAt": "2026-06-19T10:30:00",
    "components": {
      "database": "UP",
      "redis": "UP"
    }
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Operational logs

**Método e URL:** `GET /api/admin/operations/logs?limit=50`  
**Autenticação efetiva:** `ADMIN`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Lista eventos/logs operacionais recentes.

**Quando usar:** Use para triagem rápida no painel admin.

**Regras de negócio e limitações:** `limit` é normalizado pelo controller: valores menores que 1 viram 1; valores maiores que 200 viram 200.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| limit | int | não | 50 | Quantidade de eventos retornados. | 50 |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/admin/operations/logs?limit=50' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data[] | array<object> | não | Lista de eventos operacionais. | [{...}] |

Exemplo completo:

```json
{
  "success": true,
  "message": "Logs retrieved.",
  "data": [
    {
      "timestamp": "2026-06-19T10:30:00",
      "level": "INFO",
      "message": "Service healthy"
    }
  ],
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## Public mobile download

**Método e URL:** `GET /api/public/mobile-download`  
**Autenticação efetiva:** `PUBLIC`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Expõe metadados públicos do release mobile.

**Quando usar:** Use por landing pages ou apps para verificar build disponível.

**Regras de negócio e limitações:** Endpoint público pela policy `/api/public/**`; não exige JWT.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Nenhum | - | - | Endpoint público sem body obrigatório. | - |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/api/public/mobile-download' \
  -H 'Accept: application/json'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | MobileReleaseInfo retornado pelo serviço de release. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Mobile release retrieved.",
  "data": {
    "version": "1.0.0",
    "downloadUrl": "https://example.com/app.apk"
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```

## System release

**Método e URL:** `GET /system/release`  
**Autenticação efetiva:** `PUBLIC`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Expõe snapshot público da release do sistema.

**Quando usar:** Use por health pages e clientes para exibir versão/build.

**Regras de negócio e limitações:** Endpoint público pela policy `/system/release`.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Nenhum | - | - | Endpoint público sem body obrigatório. | - |

### Headers opcionais

| Nome | Tipo | Descrição | Exemplo |
|---|---|---|---|
| X-Correlation-Id | string | Rastreabilidade fim a fim. | req-20260619-0001 |
| X-Request-Id | string | Identificador do cliente para logs. | mobile-01J... |

### Path Parameters

| Nome | Tipo | Obrigatório | Descrição | Exemplo | Restrições |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/system/release' \
  -H 'Accept: application/json'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data | object | não | ReleaseSnapshot retornado pelo serviço de release. | {...} |

Exemplo completo:

```json
{
  "success": true,
  "message": "Release retrieved.",
  "data": {
    "version": "1.0.0",
    "commit": "abcdef",
    "generatedAt": "2026-06-19T10:30:00"
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 200 | OK | Operação descrita acima. | Consumir response conforme schema. |

### Exemplo de erro

```json
{
  "success": false,
  "message": "Request rejected or validation failed",
  "data": null,
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-19T10:30:00"
}
```
