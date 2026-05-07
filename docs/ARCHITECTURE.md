# Architecture

## Visao geral

O backend Kerosene segue um estilo de monolito modular com tendencias de Clean Architecture/Hexagonal:

- entrada via controllers HTTP e endpoints STOMP/WebSocket
- casos de uso e servicos de aplicacao em `application`, `service` e `orchestrator`
- contratos de saida modelados como `Port`, `Gateway` e adapters
- persistencia e integracoes em `infra`, `repository`, `persistence`
- entidades e modelos de dominio em `domain`, `entity` e `model`

Os testes em `src/test/java/source/architecture/ArchitectureGuardrailsTest.java` reforcam parte desse desenho:

- camadas `application` e `domain` nao devem depender de controllers
- a camada `domain` nao deve depender de Spring nem de adapters de infraestrutura
- `ObjectMapper` deve ser criado apenas em configuracao Spring

## Diagrama logico

```text
Client App / Frontend / Internal Operators
        |
        | HTTP JSON + JWT / TOTP / Passkey
        | STOMP over WebSocket
        v
Spring MVC Controllers + STOMP Handlers
        |
        | filters, auth, validation, request context
        v
Use Cases / Application Services / Orchestrators
        |
        +--> Domain Models / Policies / Calculators
        |
        +--> Ports / Gateways / Adapters
                 |
                 +--> PostgreSQL (JPA entities + repositories)
                 +--> Redis (state, cache, throttling, idempotency)
                 +--> Vault / MPC sidecar
                 +--> Bitcoin Core RPC/ZMQ, LND gRPC, Custody, Onramp
                 +--> WebSocket push channels
```

## Modulos de dominio

### `auth`

Responsabilidades principais:

- signup, login e segunda etapa TOTP
- PoW challenge para rotas publicas
- WebAuthn/passkeys
- recovery de emergencia
- perfil de seguranca da conta
- rate limiting, validacao de credenciais e estado temporario de onboarding

Subestruturas relevantes:

- `auth/controller`: endpoints de autenticacao
- `auth/application/orchestrator`: fluxos compostos como signup, login e recovery
- `auth/application/service`: validacoes, cripto, cache, passkey, recovery e seguranca de conta
- `auth/application/infra/persistence`: adapters JPA e Redis

### `wallet`

Responsabilidades principais:

- CRUD funcional de carteiras
- perfis de wallet
- recuperacao/uso de XPUBs
- ligacao entre identidade do usuario e carteira operacional

### `ledger`

Responsabilidades principais:

- transferencias internas e saldo
- historico e consolidacao contabile
- payment requests internos
- auditoria Merkle
- proof-of-reserves e configuracao de tesouraria
- eventos realtime de saldo e pagamento

### `transactions`

Responsabilidades principais:

- enderecos de deposito
- fee estimation, unsigned transaction, broadcast e status
- pagamentos on-chain e Lightning
- links de pagamento
- monitoramento de depositos e transferencias pendentes
- onramp e integracao com provedores externos

O pacote combina dois estilos:

- services diretos de negocio em `transactions/service`
- casos de uso mais recentes em `transactions/application/...`

### `treasury`

Responsabilidades principais:

- reservas e liquidez
- balanceamento entre saldos internos e ativos externos
- auditoria financeira
- configuracao global de limites e XPUB de auditoria
- coleta de receita/plataforma

### `voucher`

Responsabilidades principais:

- emissao e confirmacao de voucher
- links publicos de onboarding
- confirmacoes simuladas para cenarios de desenvolvimento

### `mining`

Responsabilidades principais:

- catalogo de rigs
- alocacoes de mineracao
- liquidacao e historico associados

### `notification`

Responsabilidades principais:

- push de notificacoes em `WebSocket`
- envio para filas por usuario

### `security`

Responsabilidades principais:

- bootstrap de chave mestra via Vault
- armazenamento RAM-only da chave de criptografia
- remote attestation e STALL mode
- heartbeat para Vault via Tor
- telemetria e estado de soberania
- honeypot para endpoints publicos
- documentacao/guard-rails de egress

## Fluxo de requisicao HTTP

No caminho HTTP normal, a request passa por varios mecanismos antes de chegar ao caso de uso:

1. filtros servlet/transversais como MDC, logging e honeypot
2. `ParanoidSecurityFilter`, que reforca content-type, tamanho do payload, sanitizacao de headers e padding/tempo constante em rotas sensiveis
3. `RateLimitFilter`, com contagem por IP/minuto em Redis
4. `JwtAuthenticationFilter`, que autentica `Authorization: Bearer ...` e pode renovar o token pelo header `X-New-Token`
5. controller Spring MVC
6. services/orchestrators/use cases
7. adapters de persistencia, mensageria ou provedores externos

Observacao: rotas `/ws/**` nao sao bloqueadas pelo filtro JWT na fase HTTP; a autenticacao acontece no nivel STOMP.

## Realtime e mensageria

O servico usa `Spring WebSocket + STOMP` com broker simples em memoria.

Endpoints registrados:

- `/ws/balance`
- `/ws/raw-balance`
- `/ws/payment-request`
- `/ws/raw-payment-request`

Padroes de entrega observados no codigo:

- saldo por usuario em `/user/queue/balance`
- notificacoes por usuario em `/user/queue/notifications`
- lifecycle de payment request em `/topic/payment-request/{linkId}`

Autenticacao WebSocket:

- `CONNECT` aceita token via header `Authorization` ou query param `token`
- `SUBSCRIBE` exige sessao autenticada
- heartbeats do broker: `10s/10s`

Observacao de implementacao atual:

- HTTP CORS e restrito por configuracao explicita, mas os endpoints WebSocket sao registrados com `allowedOriginPatterns("*")`

## Estado persistente e estado efemero

### PostgreSQL

O estado duravel e modelado por entidades JPA, entre elas:

- usuarios, devices e credenciais de passkey
- wallets
- ledgers, entries, transacoes e historico
- auditoria Merkle
- payment links em `financial.payment_links`
- outbox de provider externo em `financial.external_provider_outbox`
- trilha imutavel de auditoria financeira em `financial.financial_audit_events`
- runs e issues de reconciliacao financeira em `financial.financial_reconciliation_runs` e `financial.financial_reconciliation_issues`
- depositos e transferencias externas
- rigs e alocacoes de mineracao
- receita/configuracao de tesouraria

### Redis

O Redis sustenta estado operacional e efemero, incluindo:

- throttling/rate limiting
- sessoes de signup/login/recovery
- idempotencia e caches operacionais
- stores temporarios usados por flows de payment request e onboarding

Payment links nao dependem mais de Redis como fonte duravel. Redis continua valido para estado operacional efemero, caches e throttling, mas a fonte de verdade dos links criados pelo backend passa a ser Postgres.

## Jobs agendados

Ha pelo menos `15` rotinas `@Scheduled` em execucao, cobrindo:

- ticker e monitoramento de economia
- monitoramento de transacoes pendentes
- monitoramento de entradas inbound e onboarding
- liquidez e auditoria financeira
- auditoria Merkle e reconciliacao de ledger
- remote attestation, time drift e heartbeat para Vault
- expiracao/limpeza de dados operacionais

Na pratica, o backend mistura tres modelos de processamento:

- request/response sincrono
- push realtime via WebSocket
- processamento recorrente por scheduler

## Modelo de seguranca

Mecanismos mais relevantes observados no codigo:

- autenticacao stateless por JWT
- segunda etapa TOTP e recovery codes
- passkeys/WebAuthn para registro e login
- CORS HTTP restrito por `app.cors.allowed-origins`
- headers de seguranca, HSTS e supressao de `Server`
- honeypot para rotas publicas de auth
- STALL mode quando attestation falha
- token administrativo separado para operacoes de attestation/telemetry e parte da auditoria

## Dependencias externas e portas de saida

A arquitetura depende de varios adapters para sistemas externos:

- `Vault` para provisioning da chave mestra
- `MPC sidecar` para funcoes de assinatura/custodia
- provedores de custodia on-chain e Lightning
- fonte Esplora para dados Bitcoin
- provedores de onramp como MoonPay, Banxa e Bipa
- Tor como caminho de egress para recursos onion e isolamento de rede

## Notas arquiteturais importantes

- O projeto convive com codigo legadao e codigo mais recente. Por isso aparecem lado a lado `service`, `application`, `orchestrator` e controllers antigos.
- O contrato HTTP nao e centralizado em OpenAPI; a referencia atual da API esta em [API_REFERENCE_CONTROLLERS.md](API_REFERENCE_CONTROLLERS.md).
- O perfil `docker` habilita operacao conteinerizada, mas a disciplina de producao mais restrita esta concentrada em `source/config/production`.
