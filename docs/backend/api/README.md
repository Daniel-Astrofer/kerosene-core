# Backend API Documentation

Esta pasta contém a **documentação operacional canônica por domínio** da API do backend Kerosene.

Use estes arquivos para integração de frontend, mobile, QA, automação, suporte e revisão de produto. Cada documento separa a API por serviço e deve responder: para que serve, como autenticar, quais headers enviar, qual body usar, quais responses esperar e quais rotas foram removidas ou substituídas.

`docs/backend/API_REFERENCE.md` existe como referência consolidada e inventário full-text. Ele não substitui estes documentos por domínio.

Fonte principal: controllers, DTOs, `EndpointPolicyRegistry`, configuração de segurança e anotações de autorização em `backend/kerosene/src/main/java/source/**`.

## Regra KFE-only

A documentação financeira ativa deve apontar para KFE. Para novos fluxos, use:

```text
/kfe/**
/api/admin/kfe/**
```

Rotas financeiras antigas só podem aparecer como `STALE`, `CONTROLLER_ABSENT`, `REMOVED` ou orientação de migração. Elas não devem ser apresentadas como contrato ativo para clientes externos.

## Status da revisão corporativa

Os documentos por serviço foram elevados para um padrão prático de integração, cobrindo:

- finalidade do serviço e de cada endpoint ativo;
- auth efetiva;
- headers obrigatórios e opcionais;
- path/query parameters;
- request body;
- exemplos de `curl`;
- response de sucesso com body completo quando o DTO é inferível;
- explicação campo a campo;
- status codes;
- rotas legadas/stale;
- ambiguidades quando o código não fixa um contrato fechado.

## Arquivos

| Serviço | Arquivo | Estado atual |
| --- | --- | --- |
| Admin Operations | [ADMIN_OPERATIONS.md](ADMIN_OPERATIONS.md) | Endpoints ativos documentados. |
| Auditoria | [AUDIT.md](AUDIT.md) | `4` endpoints ativos em `/api/admin/kfe/audit/**`; rotas antigas `/audit/**` e `/v1/audit/**` reclassificadas como stale. |
| Auth e Conta | [AUTH.md](AUTH.md) | Endpoints ativos de autenticação, TOTP, passkey, PIN, device-key, recovery e admin access. |
| Bitcoin Accounts | [BITCOIN_ACCOUNTS.md](BITCOIN_ACCOUNTS.md) | Controller legado ausente; documento aponta para endpoints KFE ativos de carteira, UTXO e PSBT. |
| Integrações | [INTEGRATIONS.md](INTEGRATIONS.md) | Policy BTCPay existe, mas controller ausente; rota marcada como stale/controller absent. |
| KFE | [KFE.md](KFE.md) | Endpoints ativos de wallet, dashboard, receiving, transaction, quote, PSBT e auditoria KFE. |
| Ledger | [LEDGER.md](LEDGER.md) | Controller legado ausente; documento aponta para dashboard/transações/auditoria KFE. |
| Mining | [MINING.md](MINING.md) | Endpoints ativos documentados. |
| Notifications | [NOTIFICATIONS.md](NOTIFICATIONS.md) | Endpoints ativos documentados. |
| Payments | [PAYMENTS.md](PAYMENTS.md) | Fluxo ativo documentado via KFE receiving + KFE transactions; `REMOVED_LEGACY_FINANCIAL_ROUTE` legado removido. |
| Public, Health e Web | [PUBLIC_HEALTH_WEB.md](PUBLIC_HEALTH_WEB.md) | Endpoints públicos, health, web e actuator documentados. |
| Soberania e Quorum | [SOVEREIGNTY.md](SOVEREIGNTY.md) | `7` endpoints ativos documentados com HMAC shard-to-shard e admin token. |
| Transactions, Network e Economy | [TRANSACTIONS.md](TRANSACTIONS.md) | `2` endpoints ativos de Economy + referência aos endpoints KFE de transação; famílias legadas removidas. |
| Treasury | [TREASURY.md](TREASURY.md) | Controller ausente; DTO restante documentado como stale. |
| Wallet | [WALLET.md](WALLET.md) | `/wallet/**` legado removido; endpoints KFE ativos de carteira documentados. |
| DTO Schema Index | [DTO_SCHEMA_INDEX.md](DTO_SCHEMA_INDEX.md) | Índice auxiliar de DTOs. |

## Regra de leitura dos documentos

Quando um arquivo marcar um endpoint como `STALE`, `CONTROLLER_ABSENT` ou `DENIED_BY_DEFAULT`, isso significa que ele **não deve ser usado por clientes externos** até que o backend restaure controller, service e policy de segurança.

Para desenvolvimento de frontend/mobile, os caminhos preferenciais hoje são:

```text
/auth/**
/kfe/**
/api/economy/**
/api/admin/kfe/audit/**
/mining/**
/notifications/**
/health/**
/sovereignty/**
/quorum/**
```

## Regras globais relevantes

- `Security` aplica CORS explícito, CSRF desabilitado, headers defensivos e sessão stateless.
- `EndpointPolicyRegistry` classifica endpoints como `PUBLIC`, `ADMIN` ou `AUTHENTICATED`.
- O fallback de segurança é `anyRequest().denyAll()`.
- Rotas sem policy declarada podem nem alcançar o controller.
- `ParanoidSecurityFilter`, `RateLimitFilter` e `JwtAuthenticationFilter` rodam antes dos handlers REST.
- `ReleaseAttestationFilter` pode exigir headers de attestation quando habilitado por configuração.

## Principais correções desta revisão

- `SOVEREIGNTY.md` foi resolvido e expandido com headers HMAC, nonce, timestamp, assinatura, bodies, responses, status e diagrama Mermaid.
- `AUDIT.md` agora documenta os endpoints ativos reais de `KfeAuditAdminController`; controllers antigos foram marcados como removidos.
- `BITCOIN_ACCOUNTS.md`, `LEDGER.md`, `PAYMENTS.md`, `TRANSACTIONS.md`, `TREASURY.md` e `WALLET.md` foram saneados para não apresentar controllers ausentes como APIs ativas.
- `INTEGRATIONS.md` agora deixa claro que a policy BTCPay existe, mas o controller não existe no build atual.
- `KFE.md` permanece como documentação canônica para carteiras, dashboard, receiving capabilities, transações, quote, cold wallet/UTXO/PSBT e auditoria financeira ativa.

## Pendências honestas

- `DTO_SCHEMA_INDEX.md` ainda é um índice auxiliar e não substitui a documentação por endpoint.
- Alguns responses que vêm de `Map<String, Object>` não têm schema fechado no controller; nesses casos, o documento marca os campos como inferidos/representativos.
- Se algum endpoint legado for restaurado no futuro, será necessário atualizar controller, policy e documentação do respectivo serviço.
