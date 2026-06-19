# Mining API

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

API de marketplace e alocação de hashrate/mineração. Todas as rotas `/mining/**` estão autenticadas.





## Endpoints

## List mining rig offers

**Método e URL:** `GET /mining/rigs`  
**Autenticação efetiva:** `AUTHENTICATED`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Lista ofertas de rigs/hashrate disponíveis.

**Quando usar:** Use para marketplace de mineração antes de criar alocação.

**Regras de negócio e limitações:** Rota autenticada por policy `/mining/**`, mesmo sem `Authentication` no método.

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
curl -X GET 'http://localhost:8080/mining/rigs' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data[].id | long | não | ID interno da oferta. | 1 |
| data[].rigCode | string | não | Código do rig. | SHA256-A1 |
| data[].displayName | string | não | Nome comercial. | Antminer Fleet A |
| data[].algorithm | string | não | Algoritmo. | SHA-256 |
| data[].hashUnit | string | não | Unidade de hashrate. | TH/s |
| data[].availableHashrate | decimal | não | Hashrate disponível. | 100.0 |
| data[].pricePerUnitDayBtc | decimal | não | Preço por unidade/dia em BTC. | 0.00001 |
| data[].projectedBtcYieldPerUnitDay | decimal | não | Yield projetado. | 0.000012 |
| data[].minRentalHours | integer | não | Duração mínima. | 24 |
| data[].maxRentalHours | integer | não | Duração máxima. | 720 |
| data[].provider | string | não | Fornecedor. | internal |

Exemplo completo:

```json
{
  "success": true,
  "message": "Mining rig marketplace retrieved successfully.",
  "data": [
    {
      "id": 1,
      "rigCode": "SHA256-A1",
      "displayName": "Antminer Fleet A",
      "algorithm": "SHA-256",
      "hashUnit": "TH/s",
      "availableHashrate": 100,
      "pricePerUnitDayBtc": 1e-05,
      "projectedBtcYieldPerUnitDay": 1.2e-05,
      "minRentalHours": 24,
      "maxRentalHours": 720,
      "provider": "internal"
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

## Create mining allocation

**Método e URL:** `POST /mining/allocations`  
**Autenticação efetiva:** `AUTHENTICATED`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Cria uma reserva/alocação de hashrate.

**Quando usar:** Use após selecionar rig e parâmetros de mineração.

**Regras de negócio e limitações:** Pode exigir step-up por `totpCode`, `passkeyAssertionResponseJSON` ou `confirmationPassphrase`, conforme política do serviço.

### Headers obrigatórios

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| Authorization | string | sim | Bearer JWT com permissão adequada. | Bearer <JWT> |
| Content-Type | string | sim | JSON request body. | application/json |

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

| Campo | Tipo | Obrigatório | Nullable | Default | Validações | Descrição | Exemplo |
|---|---|---|---|---|---|---|---|
| walletName | string | não | sim | - | livre/enum operacional | Carteira de funding/payout. | DAILY |
| rigId | long | sim | não | - | deve existir | Rig escolhido. | 1 |
| requestedHashrate | decimal | sim | não | - | positivo | Hashrate pedido. | 10 |
| budgetBtc | decimal | sim | não | - | positivo | Orçamento em BTC. | 0.001 |
| durationHours | integer | sim | não | - | dentro da oferta | Duração. | 48 |
| payoutAddress | string | não | sim | - | endereço BTC | Destino de payout. | bc1q... |
| poolUrl | string | não | sim | - | URL stratum | Pool. | stratum+tcp://pool |
| workerName | string | não | sim | - | livre | Worker. | worker01 |
| totpCode | string | condicional | sim | - | 6 dígitos | Step-up. | 123456 |
| passkeyAssertionResponseJSON | string | condicional | sim | - | JSON WebAuthn | Step-up passkey. | {"id":"..."} |
| confirmationPassphrase | string | condicional | sim | - | segredo | Confirmação. | ******** |

Exemplo de body:

```json
{
  "rigId": 1,
  "requestedHashrate": 10,
  "budgetBtc": 0.001,
  "durationHours": 48,
  "payoutAddress": "bc1qexample",
  "workerName": "worker01",
  "totpCode": "123456"
}
```

### Exemplo de requisição

```bash
curl -X POST 'http://localhost:8080/mining/allocations' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  --data '{
  "rigId": 1,
  "requestedHashrate": 10,
  "budgetBtc": 0.001,
  "durationHours": 48,
  "payoutAddress": "bc1qexample",
  "workerName": "worker01",
  "totpCode": "123456"
}'
```

### Response de sucesso

**Status:** `201 Created`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data.id | uuid | não | ID da alocação. | 018f... |
| data.rigId | long | não | Rig alocado. | 1 |
| data.rigName | string | não | Nome do rig. | Antminer Fleet A |
| data.walletName | string | sim | Carteira associada. | DAILY |
| data.algorithm | string | não | Algoritmo. | SHA-256 |
| data.allocatedHashrate | decimal | não | Hashrate alocado. | 10 |
| data.hashUnit | string | não | Unidade. | TH/s |
| data.durationHours | integer | não | Duração. | 48 |
| data.rentalCostBtc | decimal | não | Custo. | 0.001 |
| data.projectedGrossYieldBtc | decimal | não | Yield bruto. | 0.0012 |
| data.projectedNetYieldBtc | decimal | não | Yield líquido. | 0.0002 |
| data.refundedAmountBtc | decimal | sim | Valor reembolsado. | 0 |
| data.status | string | não | Estado da alocação. | ACTIVE |
| data.providerRentalReference | string | sim | Referência do provedor. | rent-123 |
| data.payoutAddress | string | sim | Endereço de payout. | bc1q... |
| data.poolUrl | string | sim | Pool. | stratum+tcp://... |
| data.workerName | string | sim | Worker. | worker01 |
| data.startsAt | date-time | sim | Início. | 2026-06-19T10:30:00 |
| data.endsAt | date-time | sim | Fim. | 2026-06-21T10:30:00 |
| data.settledAt | date-time | sim | Liquidação. | null |

Exemplo completo:

```json
{
  "success": true,
  "message": "Mining allocation created successfully.",
  "data": {
    "id": "018f...",
    "rigId": 1,
    "rigName": "Antminer Fleet A",
    "walletName": "DAILY",
    "algorithm": "SHA-256",
    "allocatedHashrate": 10,
    "hashUnit": "TH/s",
    "durationHours": 48,
    "rentalCostBtc": 0.001,
    "projectedGrossYieldBtc": 0.0012,
    "projectedNetYieldBtc": 0.0002,
    "refundedAmountBtc": 0,
    "status": "ACTIVE",
    "providerRentalReference": "rent-123",
    "payoutAddress": "bc1qexample",
    "poolUrl": null,
    "workerName": "worker01",
    "startsAt": "2026-06-19T10:30:00",
    "endsAt": "2026-06-21T10:30:00",
    "settledAt": null
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

### Status codes específicos

| Status | Nome | Quando ocorre | Como resolver |
|---|---|---|---|
| 201 | Created | Operação descrita acima. | Consumir response conforme schema. |
| 401 | Unauthorized | JWT ausente, inválido ou expirado. | Refazer autenticação. |
| 403 | Forbidden | Token sem permissão ou policy nega acesso. | Validar role/policy. |
| 400 | Bad Request | Body inválido, enum inválido ou validação Bean Validation. | Corrigir payload. |

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

## List mining allocations

**Método e URL:** `GET /mining/allocations`  
**Autenticação efetiva:** `AUTHENTICATED`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Lista alocações do usuário autenticado.

**Quando usar:** Use para gestão do ciclo de vida de mineração.

**Regras de negócio e limitações:** Somente alocações pertencentes ao usuário do JWT devem ser retornadas/alteradas.

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
curl -X GET 'http://localhost:8080/mining/allocations' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data[] | array<MiningAllocationResponseDTO> | não | Lista de alocações. | [{...}] |
| data.id | uuid | não | ID da alocação. | 018f... |
| data.rigId | long | não | Rig alocado. | 1 |
| data.rigName | string | não | Nome do rig. | Antminer Fleet A |
| data.walletName | string | sim | Carteira associada. | DAILY |
| data.algorithm | string | não | Algoritmo. | SHA-256 |
| data.allocatedHashrate | decimal | não | Hashrate alocado. | 10 |
| data.hashUnit | string | não | Unidade. | TH/s |
| data.durationHours | integer | não | Duração. | 48 |
| data.rentalCostBtc | decimal | não | Custo. | 0.001 |
| data.projectedGrossYieldBtc | decimal | não | Yield bruto. | 0.0012 |
| data.projectedNetYieldBtc | decimal | não | Yield líquido. | 0.0002 |
| data.refundedAmountBtc | decimal | sim | Valor reembolsado. | 0 |
| data.status | string | não | Estado da alocação. | ACTIVE |
| data.providerRentalReference | string | sim | Referência do provedor. | rent-123 |
| data.payoutAddress | string | sim | Endereço de payout. | bc1q... |
| data.poolUrl | string | sim | Pool. | stratum+tcp://... |
| data.workerName | string | sim | Worker. | worker01 |
| data.startsAt | date-time | sim | Início. | 2026-06-19T10:30:00 |
| data.endsAt | date-time | sim | Fim. | 2026-06-21T10:30:00 |
| data.settledAt | date-time | sim | Liquidação. | null |

Exemplo completo:

```json
{
  "success": true,
  "message": "Mining allocation retrieved successfully.",
  "data": {
    "id": "018f...",
    "rigId": 1,
    "rigName": "Antminer Fleet A",
    "walletName": "DAILY",
    "algorithm": "SHA-256",
    "allocatedHashrate": 10,
    "hashUnit": "TH/s",
    "durationHours": 48,
    "rentalCostBtc": 0.001,
    "projectedGrossYieldBtc": 0.0012,
    "projectedNetYieldBtc": 0.0002,
    "refundedAmountBtc": 0,
    "status": "ACTIVE",
    "providerRentalReference": "rent-123",
    "payoutAddress": "bc1qexample",
    "poolUrl": null,
    "workerName": "worker01",
    "startsAt": "2026-06-19T10:30:00",
    "endsAt": "2026-06-21T10:30:00",
    "settledAt": null
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

## Get mining allocation

**Método e URL:** `GET /mining/allocations/{allocationId}`  
**Autenticação efetiva:** `AUTHENTICATED`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Busca uma alocação específica do usuário.

**Quando usar:** Use para gestão do ciclo de vida de mineração.

**Regras de negócio e limitações:** Somente alocações pertencentes ao usuário do JWT devem ser retornadas/alteradas.

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
| allocationId | uuid | sim | ID da alocação. | 018f... | UUID válido |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X GET 'http://localhost:8080/mining/allocations/{allocationId}' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data.id | uuid | não | ID da alocação. | 018f... |
| data.rigId | long | não | Rig alocado. | 1 |
| data.rigName | string | não | Nome do rig. | Antminer Fleet A |
| data.walletName | string | sim | Carteira associada. | DAILY |
| data.algorithm | string | não | Algoritmo. | SHA-256 |
| data.allocatedHashrate | decimal | não | Hashrate alocado. | 10 |
| data.hashUnit | string | não | Unidade. | TH/s |
| data.durationHours | integer | não | Duração. | 48 |
| data.rentalCostBtc | decimal | não | Custo. | 0.001 |
| data.projectedGrossYieldBtc | decimal | não | Yield bruto. | 0.0012 |
| data.projectedNetYieldBtc | decimal | não | Yield líquido. | 0.0002 |
| data.refundedAmountBtc | decimal | sim | Valor reembolsado. | 0 |
| data.status | string | não | Estado da alocação. | ACTIVE |
| data.providerRentalReference | string | sim | Referência do provedor. | rent-123 |
| data.payoutAddress | string | sim | Endereço de payout. | bc1q... |
| data.poolUrl | string | sim | Pool. | stratum+tcp://... |
| data.workerName | string | sim | Worker. | worker01 |
| data.startsAt | date-time | sim | Início. | 2026-06-19T10:30:00 |
| data.endsAt | date-time | sim | Fim. | 2026-06-21T10:30:00 |
| data.settledAt | date-time | sim | Liquidação. | null |

Exemplo completo:

```json
{
  "success": true,
  "message": "Mining allocation retrieved successfully.",
  "data": {
    "id": "018f...",
    "rigId": 1,
    "rigName": "Antminer Fleet A",
    "walletName": "DAILY",
    "algorithm": "SHA-256",
    "allocatedHashrate": 10,
    "hashUnit": "TH/s",
    "durationHours": 48,
    "rentalCostBtc": 0.001,
    "projectedGrossYieldBtc": 0.0012,
    "projectedNetYieldBtc": 0.0002,
    "refundedAmountBtc": 0,
    "status": "ACTIVE",
    "providerRentalReference": "rent-123",
    "payoutAddress": "bc1qexample",
    "poolUrl": null,
    "workerName": "worker01",
    "startsAt": "2026-06-19T10:30:00",
    "endsAt": "2026-06-21T10:30:00",
    "settledAt": null
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

## Cancel mining allocation

**Método e URL:** `POST /mining/allocations/{allocationId}/cancel`  
**Autenticação efetiva:** `AUTHENTICATED`  
**Tipo de resposta:** `ApiResponse envelope`

**O que faz:** Cancela uma alocação quando a regra de negócio permitir.

**Quando usar:** Use para gestão do ciclo de vida de mineração.

**Regras de negócio e limitações:** Somente alocações pertencentes ao usuário do JWT devem ser retornadas/alteradas.

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
| allocationId | uuid | sim | ID da alocação. | 018f... | UUID válido |

### Query Parameters

| Nome | Tipo | Obrigatório | Default | Descrição | Exemplo |
|---|---|---|---|---|---|
| Nenhum | - | - | - | - | - |

### Request Body

Este endpoint não recebe body.


### Exemplo de requisição

```bash
curl -X POST 'http://localhost:8080/mining/allocations/{allocationId}/cancel' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer <JWT>'
```

### Response de sucesso

**Status:** `200 OK`  
**Descrição:** Operação concluída.

Campos retornados:

| Campo | Tipo | Nullable | Descrição | Exemplo |
|---|---|---|---|---|
| data.id | uuid | não | ID da alocação. | 018f... |
| data.rigId | long | não | Rig alocado. | 1 |
| data.rigName | string | não | Nome do rig. | Antminer Fleet A |
| data.walletName | string | sim | Carteira associada. | DAILY |
| data.algorithm | string | não | Algoritmo. | SHA-256 |
| data.allocatedHashrate | decimal | não | Hashrate alocado. | 10 |
| data.hashUnit | string | não | Unidade. | TH/s |
| data.durationHours | integer | não | Duração. | 48 |
| data.rentalCostBtc | decimal | não | Custo. | 0.001 |
| data.projectedGrossYieldBtc | decimal | não | Yield bruto. | 0.0012 |
| data.projectedNetYieldBtc | decimal | não | Yield líquido. | 0.0002 |
| data.refundedAmountBtc | decimal | sim | Valor reembolsado. | 0 |
| data.status | string | não | Estado da alocação. | ACTIVE |
| data.providerRentalReference | string | sim | Referência do provedor. | rent-123 |
| data.payoutAddress | string | sim | Endereço de payout. | bc1q... |
| data.poolUrl | string | sim | Pool. | stratum+tcp://... |
| data.workerName | string | sim | Worker. | worker01 |
| data.startsAt | date-time | sim | Início. | 2026-06-19T10:30:00 |
| data.endsAt | date-time | sim | Fim. | 2026-06-21T10:30:00 |
| data.settledAt | date-time | sim | Liquidação. | null |

Exemplo completo:

```json
{
  "success": true,
  "message": "Mining allocation retrieved successfully.",
  "data": {
    "id": "018f...",
    "rigId": 1,
    "rigName": "Antminer Fleet A",
    "walletName": "DAILY",
    "algorithm": "SHA-256",
    "allocatedHashrate": 10,
    "hashUnit": "TH/s",
    "durationHours": 48,
    "rentalCostBtc": 0.001,
    "projectedGrossYieldBtc": 0.0012,
    "projectedNetYieldBtc": 0.0002,
    "refundedAmountBtc": 0,
    "status": "ACTIVE",
    "providerRentalReference": "rent-123",
    "payoutAddress": "bc1qexample",
    "poolUrl": null,
    "workerName": "worker01",
    "startsAt": "2026-06-19T10:30:00",
    "endsAt": "2026-06-21T10:30:00",
    "settledAt": null
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
