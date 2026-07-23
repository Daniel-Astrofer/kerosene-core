# KFE P2P Seller Payment Providers

P2P in KFE: seller gets fiat in own provider account; KFE orchestrates/verifies. Fiat never through Kerosene.

Flow: open order → seller linked provider → charge tied to order → webhook/poll → validate amount/currency/payee/status/ref → confirm → crypto/escrow per KFE.

## Security
- Prefer OAuth/connected account (no raw seller creds)
- Manual API key = controlled fallback: encrypt at rest, never echo, min scope, required connect test, rotate/revoke, audit, disable in prod if OAuth exists

## Place
- Module `:kfe-service` only
```text
com.kerosene.kfe.p2p.{controller,dto,model,repository,service,provider,provider.mercadopago}
```
- Core: auth user only; no MP/seller secrets

## SellerProviderAccount
- fields: `id`, `sellerUserId`, `provider`, `connectionMode`, `externalAccountId`, `displayName`, `country`, `currency`, `status`, `credentialRef`, `capabilitiesJson`, `lastVerifiedAt`, `createdAt`, `updatedAt`

## P2pOrder
- fields: `id`, `publicId`, `buyerUserId`, `sellerUserId`, `sellerProviderAccountId`, `fiatAmountMinor`, `fiatCurrency`, `cryptoAsset`, `cryptoAmountSats`, `priceSnapshotJson`, `status`, `expiresAt`, `createdAt`, `updatedAt`
- status: see enum in source plan
- `DRAFT`, `AWAITING_BUYER_PAYMENT`, `PROVIDER_PAYMENT_PENDING`, `PROVIDER_PAYMENT_CONFIRMED`, `PROVIDER_PAYMENT_MISMATCH`, `PROVIDER_PAYMENT_REVERSED`, `CRYPTO_ESCROW_LOCKED`, `CRYPTO_RELEASED`, `CANCELLED`, `EXPIRED`, `DISPUTED`, `REQUIRES_RECONCILIATION`

## P2pProviderPayment
- fields: `id`, `p2pOrderId`, `provider`, `providerPaymentId`, `providerOrderId`, `providerPreferenceId`, `providerStatusRaw`, `normalizedStatus`, `amountMinor`, `currency`, `payerExternalId`, `sellerExternalAccountId`, `paymentMethod`, `paidAt`, `rawPayloadHash`, `rawPayloadEncryptedRef` ou storage redigido`, `createdAt`, `updatedAt`

## P2pProviderWebhookEvent
- fields: `id`, `provider`, `eventId`, `topic`, `action`, `providerPaymentId`, `signatureValid`, `receivedAt`, `processedAt`, `status`, `payloadHash`, `payloadRedactedJson`

## P2pProviderReconciliationJob
- fields: `id`, `provider`, `sellerProviderAccountId`, `p2pOrderId`, `nextRunAt`, `attempts`, `lastErrorCode`, `lastErrorMessage`, `status`

## Provider port

## Mercado Pago
- First adapter: OAuth preferred; manual access token fallback

## Confirm invariants
- `providerPaymentId` pertence à ordem esperada;
- status normalizado é `APPROVED` ou equivalente;
- moeda é a moeda da ordem;
- valor recebido é exatamente o esperado, ou dentro de tolerância configurada;
- recebedor externo é a conta conectada do seller;
- pagamento não está cancelado, estornado, em disputa ou chargeback;
- evento de webhook tem assinatura válida quando o provider suporta assinatura;
- consulta ativa ao provider confirma o mesmo status do webhook;
- idempotência garante que o mesmo provider payment não confirme duas ordens.

## Never trust webhook alone

## Proposed API
- `POST /kfe/p2p/seller/provider-accounts`
- `POST /kfe/p2p/seller/provider-accounts/{id}/test`
- `POST /kfe/p2p/seller/provider-accounts/{id}/revoke`
- `POST /kfe/p2p/orders`
- `POST /kfe/p2p/orders/{orderId}/cancel`
- `POST /kfe/p2p/orders/{orderId}/dispute`
- `POST /api/public/kfe/p2p/providers/{provider}/webhooks`

## Tables / audit / UI errors / phases
### Tabelas sugeridas
- unique `(provider, provider_payment_id)` em `p2p_provider_payments`;
- index `(seller_user_id, status, created_at)` em provider accounts;
- index `(buyer_user_id, status, created_at)` e `(seller_user_id, status, created_at)` em orders;
- unique `(provider, event_id)` em webhook events quando o provider fornece event id;
- index `(status, next_run_at)` em reconciliation jobs.

### Auditoria

### Estados de erro que precisam aparecer na UI
- provider não conectado;
- credencial expirada/revogada;
- webhook inválido;
- pagamento menor que o esperado;
- pagamento maior que o esperado;
- moeda divergente;
- pagamento recebido em conta diferente;
- pagamento aprovado depois da ordem expirar;
- pagamento estornado/chargeback após confirmação;
- provider indisponível;
- reconciliação pendente.

### Política de confirmação e disputa

### Fases de implementação
### Fase 1 — Núcleo provider-agnostic
- criar models/repositories P2P;
- criar enums e DTOs;
- criar `SellerPaymentProviderAdapter`;
- criar APIs de provider account e order;
- criar estado de reconciliação sem Mercado Pago real;
- testes unitários de invariantes.
### Fase 2 — Mercado Pago OAuth adapter
- criar OAuth start/callback;
- armazenar credential criptografada;
- `testConnection`;
- criar checkout/cobrança;
- buscar pagamento por id;
- normalizar status.
### Fase 3 — Webhooks e reconciliação
- endpoint público de webhook;
- validação de assinatura;
- dedupe de evento;
- job de reconciliação;
- confirmação somente após fetch ativo;
- testes de duplicidade, fora de ordem e payload inválido.
### Fase 4 — P2P escrow e release
- travar cripto do seller antes de instruir buyer;
- release para buyer após confirmação provider;
- expiração e cancelamento seguros;
- disputa/chargeback.
### Fase 5 — Admin e risk
- painel admin de provider accounts;
- histórico de webhooks;
- reconciliação manual;
- limites por seller;
- score de confiabilidade;
- bloqueio automático por chargeback/fraude.

### Decisão recomendada
