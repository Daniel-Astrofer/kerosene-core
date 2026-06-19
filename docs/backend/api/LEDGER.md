# Ledger API

Documentação corporativa da antiga família Ledger e de seus substitutos ativos.

Fonte real inspecionada:

- Lista atual de controllers em `backend/kerosene/src/main/java/**`.
- `backend/kerosene/src/main/java/source/kfe/controller/KfeDashboardController.java`.
- `backend/kerosene/src/main/java/source/kfe/controller/KfeTransactionController.java`.
- `backend/kerosene/src/main/java/source/common/security/EndpointPolicyRegistry.java`.

## Estado real do serviço

A documentação anterior citava `LedgerController`, mas esse controller não existe no código-fonte atual. Portanto, `REMOVED_LEGACY_FINANCIAL_ROUTE` não é API ativa neste build.

O comportamento de ledger/saldos atualmente exposto para clientes passa por KFE:

| Necessidade | Endpoint ativo | Método | Documento |
| --- | --- | --- | --- |
| Saldo total e por carteira | `/kfe/dashboard` | `GET` | `KFE.md` |
| Carteiras e balances | `/kfe/wallets` | `GET` | `KFE.md` |
| Criar transação/lançamento financeiro | `/kfe/transactions` | `POST` | `KFE.md` |
| Consultar transação | `/kfe/transactions/{transactionId}` | `GET` | `KFE.md` |
| Auditoria de eventos financeiros | `/api/admin/kfe/audit/events` | `GET` | `AUDIT.md` / `KFE.md` |

## Headers para o fluxo ativo KFE

| Nome | Tipo | Obrigatório | Descrição | Exemplo |
| --- | --- | --- | --- | --- |
| `Authorization` | string | Sim | JWT Bearer. | `Bearer <JWT>` |
| `Content-Type` | string | Sim em transações | JSON. | `application/json` |
| `Accept` | string | Opcional | JSON. | `application/json` |
| `X-Correlation-Id` | string | Recomendado | Rastreabilidade contábil. | `ledger-20260619-0001` |
| `X-Idempotency-Key` | string | Recomendado quando o endpoint aceitar | Evita duplicidade de mutações financeiras. | `txn-01J...` |

## Endpoint ativo: Dashboard de saldos

```http
GET /kfe/dashboard
```

### O que faz

Retorna visão agregada de saldo total e saldos por carteira. É o substituto externo do antigo ledger read model.

### Quando usar

- Home do app.
- Tela de carteira.
- Reconciliação visual de saldo total e saldos por método de custódia.

### Response de sucesso

Status: `200 OK`

```json
{
  "success": true,
  "message": "KFE dashboard retrieved.",
  "data": {
    "totalBalanceSats": 150000,
    "availableBalanceSats": 149000,
    "pendingBalanceSats": 1000,
    "wallets": [
      {
        "walletId": "018f5d42-7b46-7d9f-9a1b-c405c8d6e020",
        "kind": "INTERNAL",
        "label": "Carteira global",
        "balanceSats": 100000,
        "availableBalanceSats": 100000,
        "activeAddress": null,
        "status": "ACTIVE"
      }
    ],
    "statement": []
  },
  "timestamp": "2026-06-19T12:00:00"
}
```

## Endpoint ativo: Submeter transação KFE

```http
POST /kfe/transactions
```

### O que faz

Cria uma transação financeira KFE e produz eventos auditáveis.

### Uso correto

1. Consultar `/kfe/users/{receiverIdentifier}/receiving-capabilities` se houver recebedor externo/interno.
2. Consultar `/kfe/transactions/quote` para obter valores finais.
3. Submeter `/kfe/transactions` com chave de idempotência.
4. Consultar `/kfe/transactions/{transactionId}`.

### Observação

O schema completo está em `KFE.md`, que é a documentação canônica de transações ativas.

## Rotas legadas removidas

As rotas antigas abaixo dependiam de `LedgerController`, ausente no código atual:

| Rota antiga | Estado | Substituto |
| --- | --- | --- |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | `STALE` | `GET /kfe/wallets` ou `GET /kfe/dashboard` |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | `STALE` | `GET /kfe/dashboard` |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | `STALE` | Filtrar `GET /kfe/wallets` |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | `STALE` | `GET /kfe/dashboard` statement ou auditoria KFE |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | `STALE` | `POST /kfe/transactions` |
| `REMOVED_LEGACY_FINANCIAL_ROUTE` | `STALE` | Fluxos KFE atuais/futuros |

## Status codes

| Status | Quando ocorre | Como resolver |
| --- | --- | --- |
| `200 OK` | Dashboard/listagens/consulta retornados. | Consumir `data`. |
| `201 Created` | Recurso criado em endpoint ativo que crie entidade. | Persistir ID. |
| `400 Bad Request` | Payload inválido. | Corrigir body. |
| `401 Unauthorized` | JWT ausente/inválido. | Reautenticar. |
| `403 Forbidden` | Rota legada sem controller/policy ou token insuficiente. | Usar KFE ativo. |
| `404 Not Found` | Controller legado ausente ou recurso não encontrado. | Conferir path/ID. |
| `409 Conflict` | Idempotência ou conflito de estado financeiro. | Consultar transação existente. |
| `422 Unprocessable Entity` | Regra contábil/financeira não satisfeita. | Ajustar saldo/estado. |
| `500 Internal Server Error` | Falha inesperada. | Investigar logs. |

## Nota de manutenção

Se uma API Ledger separada voltar a existir, será necessário restaurar controller, declarar policy em `EndpointPolicyRegistry`, documentar DTOs reais e explicitar se ela é pública, autenticada ou apenas administrativa.
