# Plano sequencial de refatoração do backend Kerosene

## Regra obrigatória para agentes de implementação

A partir desta fase, qualquer agente que receba tarefa de implementação no backend deve seguir obrigatoriamente:

```text
docs/backend/KEROSENE_BACKEND_ENGINEERING_DESIGN_SYSTEM.md
```

Essa regra não é consultiva. O Design System passa a ser contrato de implementação. Nenhum dispatch de alteração deve ser enviado sem incluir:

1. referência explícita ao Design System;
2. arquivos exatos permitidos para leitura;
3. arquivos exatos permitidos para alteração;
4. camada arquitetural afetada;
5. checklist de erro, log, transação, idempotência, segurança e teste;
6. comando de validação específico.

Se uma implementação necessária entrar em conflito com o Design System, o agente deve parar e reportar o conflito em vez de improvisar outro padrão.

Data da análise: 2026-06-20  
Escopo: `backend/kerosene`, `backend/vault`, `backend/mpc-sidecar`, `backend/kerosene-infrastructure`, `backend/tests`.

Este documento consolida a análise orquestrada dos agentes Codex em modo **read-only**. Nenhum agente recebeu permissão de escrita. A única escrita desta rodada é este arquivo de plano.

## 1. Premissas de orquestração

A estratégia usada foi separar **leitura/triagem** de **alteração de código**.

Os agentes não receberam permissão para procurar livremente pelo repositório. Cada agente recebeu uma lista fechada de arquivos permitidos, para reduzir latência, custo de contexto e risco de alterar ou ler áreas erradas.

Agentes usados:

| Agente | Escopo | Modo | Resultado |
|---|---|---:|---|
| `codex2` | autenticação, JWT, admin, passkey, device-key, logging e policy registry | read-only | concluído |
| `codex5` | KFE financeiro, ledger, idempotência, outbox, payment request, PSBT, reserve, tax | read-only | concluído |
| `codex6` | Vault, MPC sidecar, attestation, sovereign/quorum, provisioning | read-only | concluído |
| `codex7` | infra, migrations, prod config, observability, testes | read-only | concluído |

Estado importante do repositório antes da escrita deste plano:

- A árvore já estava suja antes desta tarefa, com muitos arquivos modificados e não rastreados.
- Existem alterações não rastreadas relevantes em `backend/kerosene/src/main/java/source/kfe/**` e migrations `V18` a `V25`.
- Não se deve aplicar refatoração automática antes de preservar o estado atual com branch/commit/stash controlado.

## 2. Mapa crítico do backend

Módulos principais encontrados:

```text
backend/kerosene                 Java/Spring, Gradle
backend/vault                    Java/Maven, serviço Vault separado
backend/mpc-sidecar              Go, sidecar de assinatura/chaves
backend/kerosene-infrastructure  manifests, observabilidade, prod, vault
backend/tests                    testes e scripts auxiliares
backend/adapters                 adaptadores Bitcoin/Lightning
```

Arquivos de build críticos:

```text
backend/kerosene/build.gradle.kts
backend/kerosene/settings.gradle.kts
backend/vault/pom.xml
backend/mpc-sidecar/go.mod
backend/mpc-sidecar/go.sum
```

Áreas de risco identificadas:

1. Migrações e configuração de produção.
2. Sessão/JWT/admin-token.
3. Ledger/idempotência/outbox do KFE.
4. Vault/MPC/attestation/provisioning.
5. Payment requests, endereços, PSBT e tax/reserve reporting.
6. Testes e observabilidade.

## 3. Decisão global de prioridade

**Nota operacional revisada em 2026-06-20:** os dados existentes do ambiente atual são apenas dev/test. Portanto, perda de dados existentes não deve bloquear a refatoração. O objetivo da Fase 0 passa a ser estabilizar schema, migrations, baseline e configuração para que as fases seguintes sejam validadas em uma base limpa e reproduzível.

A ordem recomendada é:

```text
Fase 0  Estabilizar migrações/configuração sem preservar dados dev/test
Fase 0.5 Criar Design System de Backend e contrato técnico dos agentes
Fase 1  Corrigir sessão/JWT/admin-token mínimo
Fase 2  Criar testes de invariantes financeiras
Fase 3  Corrigir idempotência, ledger, state machine e outbox
Fase 4  Corrigir inbound/reconciliation/payment requests/PSBT
Fase 5  Alinhar Vault/MPC/attestation/provisioning
Fase 6  Endurecer produção, observabilidade e cobertura
```

Justificativa revisada: como os dados atuais são apenas dev/test, perda de dados existentes não é bloqueador. Ainda assim, migrações e configuração precisam ser estabilizadas primeiro para evitar drift de schema, baseline Flyway mascarando erro, reuso incorreto de cursores/índices e testes financeiros rodando sobre um banco inconsistente. Depois disso, os testes financeiros precisam de sessões confiáveis para simular atores, permissões e invalidação. Só então faz sentido mexer no ledger. Antes das alterações de código, a Fase 0.5 fixa o padrão de coesão para impedir que agentes refatorem módulos diferentes com estilos incompatíveis.

## 4. Achados P0 consolidados

### P0.1 — `V23__drop_legacy_financial_tables.sql` é destrutiva, mas aceitável para dev/test

Arquivo principal:

```text
backend/kerosene/src/main/resources/db/migration/V23__drop_legacy_financial_tables.sql
```

Achado:

- A migration derruba diversas tabelas financeiras/ledger/audit com `CASCADE`.
- A perda dos dados atuais é aceitável porque o ambiente informado é dev/test.
- `V25__repair_custodial_derivation_cursors.sql` recria cursor vazio depois de V23, o que pode causar reuso de índices de derivação de endereço.

Prioridade revisada: **não é bloqueador por perda de dados em dev/test**. Continua sendo etapa inicial para garantir schema limpo, ordem correta de migrations e evitar efeitos colaterais como reuso de cursores/índices.

### P0.2 — Flyway pode mascarar drift de produção

Arquivos principais:

```text
backend/kerosene/src/main/resources/application-prod.properties
backend/kerosene/src/main/resources/application-docker.properties
```

Achado:

- `spring.flyway.baseline-on-migrate` aparece com default permissivo.
- Em DB não vazio, isso pode ocultar migrations não aplicadas.
- O app pode subir em estado incompatível com Hibernate validate ou, pior, com dados parcialmente migrados.

Prioridade: **fase 0**.

### P0.3 — Configuração Kubernetes não casa com propriedades Spring

Arquivo principal:

```text
backend/kerosene-infrastructure/prod/k8s/kerosene-app.yaml
```

Achados:

- Manifest usa variáveis como `POSTGRES_URL`, `REDIS_HOST`, `LND_HOST`, `LND_MACAROON_HEX`.
- Properties esperam nomes como `spring.datasource.url`, `spring.data.redis.host`, `LIGHTNING_LND_HOST`, `LIGHTNING_LND_MACAROON` ou path equivalente.
- Risco: produção tenta localhost, LND indisponível ou safety check falha no boot.

Prioridade: **fase 0**.

### P0.4 — JWT/session renewal perde roles e não há revogação real

Arquivos principais:

```text
backend/kerosene/src/main/java/source/auth/application/service/validation/jwt/JwtService.java
backend/kerosene/src/main/java/source/auth/application/service/validation/jwt/contracts/JwtServicer.java
backend/kerosene/src/main/java/source/auth/application/infra/security/JwtAuthenticationFilter.java
```

Achados:

- Renovação de token chama geração por `userId`, podendo perder roles existentes.
- `jti` é derivado do usuário e não representa uma sessão única revogável.
- Um token roubado continua válido até expirar.

Prioridade: **após migração safety, antes de testes financeiros complexos**.

### P0.5 — Admin polling pode transformar UUID em bearer credential

Arquivos principais:

```text
backend/kerosene/src/main/java/source/auth/application/service/admin/AdminAccessService.java
backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java
backend/kerosene/src/main/java/source/common/security/EndpointPolicyRegistry.java
```

Achado:

- O polling de login admin por `attemptId` pode retornar token admin para quem possui o UUID.
- Deve ser amarrado ao device/session inicial ou a um verificador de uso único.

Prioridade: **fase 1**, ou fase 0 se admin for usado no fluxo de migração/operação.

### P0.6 — Idempotência financeira inconsistente

Arquivos principais:

```text
backend/kerosene/src/main/java/source/kfe/model/KfeTransactionEntity.java
backend/kerosene/src/main/java/source/kfe/model/KfeIdempotencyEntity.java
backend/kerosene/src/main/java/source/kfe/application/transaction/KfeSubmitTransactionUseCase.java
backend/kerosene/src/main/java/source/kfe/application/transaction/KfeTransactionIdempotencyUseCase.java
```

Achados:

- A entidade de idempotência é escopada por `(userId, idempotencyKey)`.
- A transação parece ter `idempotency_key` globalmente único.
- A transação é criada antes de reservar a idempotência.
- Concorrência pode criar linhas órfãs ou falhar com erro de constraint em vez de resposta idempotente.

Prioridade: **fase 3**, depois de testes e migration safety.

### P0.7 — Outbox pode executar duas vezes

Arquivos principais:

```text
backend/kerosene/src/main/java/source/kfe/service/KfeExecutionOutboxProcessor.java
backend/kerosene/src/main/java/source/kfe/repository/KfeExecutionOutboxRepository.java
backend/kerosene/src/main/java/source/kfe/service/KfeExecutionTransactionHelper.java
backend/kerosene/src/main/java/source/kfe/model/KfeExecutionOutboxEntity.java
```

Achados:

- Claims `PROCESSING` podem ser retomados depois de timeout.
- Uma chamada lenta ao provider pode ser reprocessada e enviada novamente.
- Settlement e failure não estão plenamente idempotentes.
- Ambiguidade de provider pode liberar fundos mesmo se a operação externa tiver sido executada.

Prioridade: **fase 3**.

### P0.8 — Vault e Java client falam protocolos incompatíveis

Arquivos principais:

```text
backend/kerosene/src/main/java/source/security/VaultAttestationClient.java
backend/kerosene/src/main/java/source/security/VaultProvisioningClient.java
backend/vault/src/main/java/vault/controller/VaultController.java
backend/vault/src/main/java/vault/security/TpmAttestationService.java
```

Achados:

- Java client envia attestation `v2` challenge-bound.
- Vault aceita `v1` HMAC compartilhado.
- Java client chama `/v1/vault/challenge`, mas Vault não expõe endpoint equivalente no escopo analisado.
- Provisionamento retorna `aes_key` como Base64 JSON.

Prioridade: **bloqueador se `vault.enabled=true` ou custódia MPC real forem requisitos de produção**.

### P0.9 — Sidecar se apresenta como MPC, mas assina com chave local única

Arquivos principais:

```text
backend/mpc-sidecar/service/mpc_service.go
backend/mpc-sidecar/service/secure_enclave.go
backend/mpc-sidecar/proto/mpc.proto
backend/kerosene/src/main/java/source/kfe/service/KfeMpcKeyService.java
```

Achado:

- Sidecar aceita parâmetros de threshold, mas gera e armazena uma única chave Ed25519 local.
- Isso deve ser tratado como placeholder de signer local, não MPC real.

Prioridade: **fase 5**, com ajuste de linguagem, fail-closed e contrato real futuro.

## 5. Plano sequencial de refatoração

### Fase 0 — Reset controlado dev/test, schema limpo e segurança de migração

Objetivo: permitir reset destrutivo em dev/test, mas impedir que refatorações sejam aplicadas em cima de schema driftado, baseline permissivo ou migrations inconsistentes.

Arquivos principais:

```text
backend/kerosene/src/main/resources/db/migration/V17__financial_integrity_constraints.sql
backend/kerosene/src/main/resources/db/migration/V18__unique_active_wallet_per_custody.sql
backend/kerosene/src/main/resources/db/migration/V23__drop_legacy_financial_tables.sql
backend/kerosene/src/main/resources/db/migration/V25__repair_custodial_derivation_cursors.sql
backend/kerosene/src/main/resources/application-prod.properties
backend/kerosene/src/main/resources/application-docker.properties
backend/kerosene-infrastructure/prod/k8s/kerosene-app.yaml
```

Ações:

1. Criar branch de segurança antes de qualquer alteração.
2. Registrar estado atual do schema esperado para uma base dev/test limpa.
3. Permitir que V23 seja destrutiva em dev/test, desde que isso esteja explícito e reproduzível.
4. Remover a exigência de backup/export/backfill para o ambiente atual de dev/test.
5. Garantir que `custodial_derivation_cursors` não gere reuso incorreto de índice/endereço depois do reset.
6. Tornar Flyway baseline fail-closed em produção; qualquer override deve ser manual e documentado.
7. Alinhar nomes de variáveis do Kubernetes com nomes Spring efetivos.
8. Adicionar teste de binding de config de produção e teste de migração clean DB.

Critério de saída:

- Migração clean DB passa.
- Reset/migração dev-test limpa passa de forma reproduzível.
- Migração destrutiva fica explicitamente marcada como aceitável apenas para dev/test ou ambientes descartáveis.
- Config prod resolve datasource, Redis, LND, Vault e MPC corretamente.

### Fase 0.5 — Backend Engineering Design System

Objetivo: fixar a constituição técnica do backend antes de agentes realizarem alterações de código em múltiplos módulos.

Arquivo principal:

```text
docs/backend/KEROSENE_BACKEND_ENGINEERING_DESIGN_SYSTEM.md
```

Ações:

1. Usar o Design System como contrato obrigatório para novas refatorações.
2. Exigir que agentes de alteração recebam arquivos exatos e escopo fechado.
3. Padronizar camadas: controller, application/usecase, domain, ports e infrastructure/adapters.
4. Padronizar modelo de erro público, logs estruturados, transações e idempotência.
5. Proibir retorno público de `e.getMessage()` e stack traces internos.
6. Proibir chamada externa antes de reserva idempotente em fluxos financeiros.
7. Separar signer local dev/test de MPC real por nome, config e fail-closed em produção.
8. Exigir teste que falharia antes para bug fix e regra crítica.

Critério de saída:

- Documento criado e referenciado no plano sequencial.
- Próximos dispatches de agentes incluem o Design System como padrão obrigatório.
- Nenhuma fase de refatoração ampla começa sem checklist de camada, erro, log, idempotência e teste.

### Fase 1 — Sessões, JWT, admin polling e step-up mínimo

Objetivo: garantir que testes financeiros e administrativos tenham identidade confiável.

Arquivos principais:

```text
backend/kerosene/src/main/java/source/auth/application/service/validation/jwt/JwtService.java
backend/kerosene/src/main/java/source/auth/application/service/validation/jwt/contracts/JwtServicer.java
backend/kerosene/src/main/java/source/auth/application/infra/security/JwtAuthenticationFilter.java
backend/kerosene/src/main/java/source/auth/application/infra/security/Security.java
backend/kerosene/src/main/java/source/auth/application/service/admin/AdminAccessService.java
backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java
backend/kerosene/src/main/java/source/auth/controller/TotpController.java
backend/kerosene/src/main/java/source/auth/controller/AccountSecurityController.java
backend/kerosene/src/main/java/source/common/security/EndpointPolicyRegistry.java
```

Ações:

1. Criar session id ou token id único por login.
2. Preservar roles na renovação de JWT.
3. Adicionar verificação de revogação no filtro JWT.
4. Invalidar sessões em mudanças sensíveis de segurança.
5. Exigir step-up para desativar TOTP e reduzir nível de segurança.
6. Amarrar polling admin ao device/session iniciador ou token de uso único.
7. Sanear respostas de erro que hoje podem devolver `exception.getMessage()`.
8. Padronizar resposta de token: evitar mistura entre `jwt` e `"userId jwt"`.

Testes mínimos:

```text
Admin JWT near-expiry renova mantendo ROLE_ADMIN.
JWT revogado é rejeitado pelo filtro.
Desativar TOTP exige step-up.
Downgrade de account security exige step-up.
Polling admin não entrega token apenas com attemptId vazado.
EndpointPolicyRegistry mantém rotas públicas e privadas coerentes.
```

### Fase 2 — Testes de invariantes financeiras antes de mudar lógica

Objetivo: criar tripwires antes de alterar ledger, outbox ou state machine.

Arquivos principais:

```text
backend/kerosene/src/test/java/source/kfe/service/KfeTransactionEngineTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeExecutionOutboxProcessorTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeInboundSettlementServiceTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeReceiveAddressIssuerTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeWalletServiceTest.java
```

Invariantes a provar:

1. Nenhuma transação duplica débito para o mesmo `(userId, idempotencyKey, payload)`.
2. Mesma idempotency key com payload diferente falha sem criar reserva/transação extra.
3. `AVAILABLE + LOCKED + PENDING` obedece movimentos esperados.
4. Settlement é idempotente.
5. Failure final libera reserva apenas uma vez.
6. Resultado ambíguo mantém fundos bloqueados e exige reconciliação.
7. Inbound proof duplicada credita apenas uma vez.
8. Proof divergente não marca outro outbox como dispatched.
9. Transferência interna conserva valor ou registra fee explicitamente.
10. Outbox stale claim não executa provider duas vezes para transação já finalizada.

### Fase 3 — Idempotência, ledger, state machine e outbox

Objetivo: impedir duplicidade financeira e centralizar lifecycle.

Arquivos principais:

```text
backend/kerosene/src/main/java/source/kfe/model/KfeTransactionEntity.java
backend/kerosene/src/main/java/source/kfe/model/KfeIdempotencyEntity.java
backend/kerosene/src/main/java/source/kfe/model/KfeIdempotencyId.java
backend/kerosene/src/main/java/source/kfe/model/KfeExecutionOutboxEntity.java
backend/kerosene/src/main/java/source/kfe/application/transaction/KfeSubmitTransactionUseCase.java
backend/kerosene/src/main/java/source/kfe/application/transaction/KfeTransactionIdempotencyUseCase.java
backend/kerosene/src/main/java/source/kfe/application/transaction/KfeTransactionStateMachine.java
backend/kerosene/src/main/java/source/kfe/application/transaction/KfeTransactionOutboxUseCase.java
backend/kerosene/src/main/java/source/kfe/service/KfeExecutionOutboxProcessor.java
backend/kerosene/src/main/java/source/kfe/service/KfeExecutionTransactionHelper.java
backend/kerosene/src/main/java/source/kfe/service/KfeBalanceService.java
backend/kerosene/src/main/java/source/kfe/service/KfeBalanceMovementRecorder.java
backend/kerosene/src/main/java/source/kfe/repository/KfeExecutionOutboxRepository.java
backend/kerosene/src/main/resources/db/migration/<nova_migration_kfe_idempotency_outbox_constraints>.sql
```

Ações:

1. Definir formalmente se idempotência é global ou por usuário. Recomendação: por usuário.
2. Ajustar constraints para refletir essa decisão.
3. Reservar idempotência antes de criar transação definitiva.
4. Uma transação/operação deve ter no máximo um outbox ativo.
5. Toda transição passa por `KfeTransactionStateMachine`.
6. Settlement/failure devem checar status atual sob lock.
7. Provider idempotency key deve ser enviada e persistida.
8. Stale claim só pode reprocessar se operação ainda não tiver provider reference/settlement terminal.
9. Resultado ambíguo deve mover para `REQUIRES_RECONCILIATION`, não liberar saldo.
10. Fee Kerosene deve ter destino contábil explícito.

Critério de saída:

- Testes da fase 2 passam.
- Não há duplicate debit/credit/outbox em cenários concorrentes simulados.
- Falha ambígua não solta fundos.

### Fase 4 — Inbound, payment request, endereços, reserve, tax e PSBT

Objetivo: corrigir coerência de negócio ao redor do ledger.

Arquivos principais:

```text
backend/kerosene/src/main/java/source/kfe/service/KfeInboundSettlementService.java
backend/kerosene/src/main/java/source/kfe/service/KfePaymentRequestService.java
backend/kerosene/src/main/java/source/kfe/model/KfePaymentRequestEntity.java
backend/kerosene/src/main/java/source/kfe/repository/KfePaymentRequestRepository.java
backend/kerosene/src/main/java/source/kfe/service/KfeReceiveAddressIssuer.java
backend/kerosene/src/main/java/source/kfe/service/KfeDerivationCursorService.java
backend/kerosene/src/main/java/source/kfe/service/KfeWalletService.java
backend/kerosene/src/main/java/source/kfe/model/KfeWalletAddressEntity.java
backend/kerosene/src/main/java/source/kfe/repository/KfeWalletAddressRepository.java
backend/kerosene/src/main/java/source/kfe/service/KfePsbtWorkflowService.java
backend/kerosene/src/main/java/source/kfe/service/KfeReserveOverviewService.java
backend/kerosene/src/main/java/source/kfe/service/KfeTaxEventService.java
```

Ações:

1. Separar inbound passivo de outbox ativo.
2. Prova de inbound deve casar transação/outbox/provider reference/rail/amount/destination.
3. Payment request fixa deve validar underpay/overpay explicitamente.
4. Payment request deve ir `OPEN -> PAID` uma única vez e preencher `paidTransactionId`.
5. Decidir se watch-only wallet pode criar payment request. Recomendação: sim, se for receive-only e tiver xpub/descriptor válido.
6. Derivação xpub deve usar lock ou cursor transacional único.
7. Adicionar constraints: `address` único e `(wallet_id, derivation_index)` único quando não nulo.
8. Address rotation não deve invalidar payment requests já emitidos sem regra explícita.
9. Reserve overview deve separar saldo de clientes, reserva da plataforma e saldo observado on-chain para evitar double-count.
10. Tax events devem filtrar eventos liquidados e representar fees/total debit corretamente.
11. PSBT deve ser decidido: utilitário administrativo ou parte do ledger. Se parte do ledger, precisa reserva, single-flight e settlement.

### Fase 5 — Vault, attestation, provisioning e MPC

Objetivo: alinhar contrato real de segurança antes de depender dele em produção.

Arquivos principais:

```text
backend/vault/src/main/java/vault/controller/VaultController.java
backend/vault/src/main/java/vault/security/TpmAttestationService.java
backend/vault/src/main/java/vault/security/ShardIdentityService.java
backend/vault/src/main/java/vault/controller/SovereigntyHeartbeatController.java
backend/vault/src/main/java/vault/service/WatchdogService.java
backend/vault/src/main/resources/application.properties
backend/vault/src/test/java/vault/controller/VaultControllerTest.java
backend/kerosene/src/main/java/source/security/VaultAttestationClient.java
backend/kerosene/src/main/java/source/security/VaultProvisioningClient.java
backend/kerosene/src/main/java/source/security/VaultBootstrapCoordinator.java
backend/kerosene/src/main/java/source/security/VaultEndpointResolver.java
backend/kerosene/src/main/java/source/security/UdsSocks5Transport.java
backend/kerosene/src/main/java/source/kfe/service/KfeMpcKeyService.java
backend/mpc-sidecar/service/mpc_service.go
backend/mpc-sidecar/service/secure_enclave.go
backend/mpc-sidecar/service/mpc_service_test.go
```

Decisão recomendada pelos agentes:

- Adotar envelope `v2` challenge-bound como alvo imediato de compatibilidade.
- Nomear explicitamente como **software identity attestation placeholder** até existir verificação TPM real.
- Não manter `v1 HMAC` como caminho de produção.
- Não chamar sidecar atual de MPC real.

Ações:

1. Definir spec de attestation/provisioning: endpoints, challenge, replay, identidade, transporte e expiração.
2. Implementar ou remover `/v1/vault/challenge` para alinhar client e server.
3. Rejeitar replay de nonce/challenge.
4. Amarrar token de provisionamento a node id, public key, challenge e TTL.
5. Criptografar resposta de provisionamento para chave pública registrada ou exigir canal mTLS real.
6. Decidir se direct HTTP é proibido em produção. Recomendação: fail-closed sem Tor UDS + mTLS/identidade equivalente.
7. Refatorar `/arm` para não enviar full master key por diretor; usar fragmentos, HSM ou cerimônia externa.
8. Definir handoff Java Vault -> Go sidecar ou declarar sidecar decoupled/local signer.
9. Corrigir heartbeat replay e locks do watchdog.
10. Renomear claims de “MPC” ou implementar threshold real.

### Fase 6 — Produção, observabilidade e cobertura

Objetivo: transformar os achados em gates permanentes.

Arquivos principais:

```text
backend/kerosene/build.gradle.kts
backend/kerosene/src/main/resources/logback-spring.xml
backend/kerosene/src/main/java/source/common/infra/logging/LoggingFilter.java
backend/kerosene/src/main/java/source/common/infra/health/OperationalHealthService.java
backend/kerosene/src/test/java/source/config/WebSocketRateLimitTest.java
backend/tests/scripts/**
backend/kerosene-infrastructure/observability/**
backend/kerosene-infrastructure/prod/k8s/**
```

Ações:

1. Trocar imagem `latest` por digest imutável.
2. Alinhar release attestation ao digest real.
3. Adicionar readiness/liveness probes.
4. Validar Prometheus targets contra serviços reais.
5. Testar WebSocket rate limit usando wiring real, não bucket isolado.
6. Adicionar thresholds de cobertura Java para safety checks, migrations/config e observabilidade.
7. Medir saturação de async appenders/audit logs.
8. Garantir access logs para endpoints sensíveis sem vazar segredo.

## 6. Ordem de execução resumida

```text
0. Criar branch/backup e congelar árvore atual.
1. Neutralizar risco de V23/Flyway/prod env.
2. Adicionar testes de migração clean e legacy-dirty.
3. Corrigir JWT/session renewal/revocation mínimo.
4. Blindar admin polling se usado em operação.
5. Adicionar testes de invariantes financeiras.
6. Corrigir idempotência e constraints.
7. Refatorar outbox/settlement/failure para idempotência real.
8. Corrigir inbound/payment request/address derivation.
9. Decidir e corrigir PSBT ledger integration.
10. Alinhar Vault attestation/provisioning.
11. Reclassificar/implementar MPC sidecar.
12. Endurecer prod observability, probes, images, coverage.
```

## 7. Perguntas feitas aos agentes e respostas verificadas

### Segurança/Auth (`codex2`)

Pergunta principal: o que deve bloquear testes financeiros?  
Resposta: JWT/session revocation e role-preserving renewal devem vir logo após migration safety. Admin polling é fase 1, ou fase 0 se usado no fluxo operacional.

Consenso aceito:

- Corrigir migração primeiro.
- Corrigir JWT/session antes de testes financeiros complexos.
- Ledger/idempotência tem prioridade sobre limpeza de UX passkey.

### Financeiro KFE (`codex5`)

Pergunta principal: qual primeiro passo financeiro?  
Resposta: testes de invariantes financeiras antes de refatorar idempotência/outbox/state machine.

Consenso aceito:

- Migration safety vem antes de schema financeiro.
- Depois vêm testes de invariantes.
- Só então alterar idempotência, outbox e settlement.

### Vault/MPC (`codex6`)

Pergunta principal: mismatch Vault/MPC é runtime blocker?  
Resposta: sim, se `vault.enabled=true` ou custódia MPC for requisito. Client e Vault falam protocolos incompatíveis.

Consenso aceito:

- Usar `v2` challenge-bound como contrato imediato.
- Rotular como placeholder até TPM real.
- Tratar Go sidecar como signer local placeholder, não threshold MPC.

### Infra/Migrations (`codex7`)

Pergunta principal: o que é fase zero?  
Resposta: V23 destructive migration e Flyway baseline são bloqueadores; validar clean/legacy DB antes de qualquer refactor.

Consenso aceito:

- Migration safety é fase 0 antes de auth/KFE/Vault.
- V23 é maior bloqueador.
- Prod config binding deve ser validado cedo.

## 8. Comandos de validação recomendados

Executar somente depois de preservar o estado atual da árvore.

```bash
# Backend principal
cd backend/kerosene
./gradlew test

# Testes focados de segurança
./gradlew test --tests '*Jwt*' --tests '*AdminAccess*' --tests '*EndpointPolicy*'

# Testes focados KFE
./gradlew test --tests '*KfeTransactionEngineTest' --tests '*KfeExecutionOutboxProcessorTest' --tests '*KfeInboundSettlementServiceTest'

# Vault
cd ../vault
mvn test

# MPC sidecar
cd ../mpc-sidecar
go test ./...
```

Validações novas que devem ser criadas:

```text
Clean-schema Flyway V1..V25 + Hibernate validate.
Legacy-dirty schema V17..V25 com fixtures representativas.
Preflight destrutivo para V23 com backup marker obrigatório.
Prod config binding usando variáveis do k8s.
Attestation Vault client/server end-to-end.
JWT revocation/renewal role-preserving.
Outbox stale claim/idempotent settlement.
Payment request OPEN -> PAID único.
Address derivation uniqueness.
```

## 9. Regras para próximos agentes de alteração

Quando for executar as fases, cada agente deve receber apenas arquivos específicos. Exemplo de contrato:

```text
Você é agente de alteração de código, não de exploração.

Objetivo:
<objetivo exato da fase>

Arquivos que pode ler:
<lista fechada>

Arquivos que pode alterar:
<lista ainda menor>

Proibido:
- procurar arquivos fora da lista
- alterar frontend
- alterar migrations fora da fase
- rodar find/grep global
- reformatar arquivos não relacionados

Validação obrigatória:
<comando específico>
```

Nunca colocar dois agentes alterando o mesmo módulo ao mesmo tempo se houver migrations, entidades ou services compartilhados.

## 10. Primeiro dispatch recomendado para alteração futura

### Agente 0.5 — Design System enforcement

Arquivos permitidos inicialmente:

```text
docs/backend/KEROSENE_BACKEND_ENGINEERING_DESIGN_SYSTEM.md
docs/backend/KEROSENE_BACKEND_SEQUENTIAL_REFACTOR_PLAN.md
```

Objetivo: garantir que todo dispatch futuro trate o Design System como contrato obrigatório de implementação antes de alterações em código. Agentes devem parar e reportar conflito se não conseguirem cumprir o padrão.

### Agente A — Fase 0 schema/config stabilization

Arquivos permitidos inicialmente:

```text
backend/kerosene/src/main/resources/application-prod.properties
backend/kerosene/src/main/resources/application-docker.properties
backend/kerosene/src/main/resources/db/migration/V17__financial_integrity_constraints.sql
backend/kerosene/src/main/resources/db/migration/V23__drop_legacy_financial_tables.sql
backend/kerosene/src/main/resources/db/migration/V25__repair_custodial_derivation_cursors.sql
backend/kerosene-infrastructure/prod/k8s/kerosene-app.yaml
```

Objetivo: permitir reset dev/test, manter produção fail-closed, corrigir drift/baseline e compatibilidade de config, sem tocar KFE logic.

### Agente B — Fase 1 JWT/session

Arquivos permitidos inicialmente:

```text
backend/kerosene/src/main/java/source/auth/application/service/validation/jwt/JwtService.java
backend/kerosene/src/main/java/source/auth/application/service/validation/jwt/contracts/JwtServicer.java
backend/kerosene/src/main/java/source/auth/application/infra/security/JwtAuthenticationFilter.java
backend/kerosene/src/main/java/source/auth/application/infra/security/Security.java
backend/kerosene/src/test/java/source/auth/controller/AdminAccessControllerAuthorizationTest.java
backend/kerosene/src/test/java/source/common/security/EndpointPolicyRegistryTest.java
```

Objetivo: preservar roles na renovação e preparar revogação, sem mexer no KFE.

### Agente C — Fase 2 testes KFE

Arquivos permitidos inicialmente:

```text
backend/kerosene/src/test/java/source/kfe/service/KfeTransactionEngineTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeExecutionOutboxProcessorTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeInboundSettlementServiceTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeReceiveAddressIssuerTest.java
backend/kerosene/src/test/java/source/kfe/service/KfeWalletServiceTest.java
```

Objetivo: criar testes de falha antes de corrigir lógica financeira.

### Agente D — Fase 5 Vault protocol

Arquivos permitidos inicialmente:

```text
backend/vault/src/main/java/vault/controller/VaultController.java
backend/vault/src/main/java/vault/security/TpmAttestationService.java
backend/vault/src/main/java/vault/security/ShardIdentityService.java
backend/vault/src/test/java/vault/controller/VaultControllerTest.java
backend/kerosene/src/main/java/source/security/VaultAttestationClient.java
backend/kerosene/src/main/java/source/security/VaultProvisioningClient.java
```

Objetivo: alinhar `/challenge`, `/attest`, `/provision` sem mexer em ledger.

## 11. Resultado esperado da refatoração completa

Ao final das fases, o backend deve ter:

- Migrações reversíveis ou explicitamente aprovadas quando destrutivas.
- Configuração prod coerente com manifests.
- JWT com sessão revogável e renovação preservando roles.
- Admin login sem redeem por UUID solto.
- Ledger com idempotência por usuário e sem duplicidade de transação/outbox.
- Settlement/failure/outbox idempotentes.
- Payment requests conectados a settlement real.
- Derivação de endereços sem reuso de índice.
- Reserve/tax coerentes com eventos liquidados.
- Vault client/server no mesmo protocolo.
- Sidecar classificado corretamente: placeholder ou MPC real.
- Testes que impedem regressão nas invariantes críticas.

## 12. Implementação executada — Fase 0 parcial — 2026-06-20

Escopo aplicado seguindo `docs/backend/KEROSENE_BACKEND_ENGINEERING_DESIGN_SYSTEM.md` como contrato obrigatório.

Arquivos alterados:

```text
backend/kerosene/src/main/resources/application-prod.properties
backend/kerosene/src/main/resources/application-docker.properties
backend/kerosene/src/main/resources/db/migration/V23__drop_legacy_financial_tables.sql
backend/kerosene-infrastructure/prod/k8s/kerosene-app.yaml
backend/kerosene/src/test/java/source/architecture/ProductionConfigurationGuardrailsTest.java
```

Mudanças:

1. `application-prod.properties` agora define explicitamente datasource e Redis por variáveis Spring efetivas.
2. `application-prod.properties` e `application-docker.properties` agora usam `spring.flyway.baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:false}`.
3. `kerosene-app.yaml` agora usa nomes de ambiente que batem com as properties efetivas: `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, `LIGHTNING_LND_*`, `VAULT_RAFT_URL`, `MPC_SIDECAR_HOST`.
4. `V23__drop_legacy_financial_tables.sql` foi marcada explicitamente como reset destrutivo dev/test, não como migration segura para produção com dados reais.
5. Foi criado `ProductionConfigurationGuardrailsTest` para bloquear regressão dessas regras.

Validação executada:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests source.architecture.ProductionConfigurationGuardrailsTest --no-daemon
```

Resultado: `BUILD SUCCESSFUL`.

Observação: o JDK default do ambiente é OpenJDK `25.0.3`, que quebra o Kotlin/Gradle antes dos testes com `IllegalArgumentException: 25.0.3`. A validação passou usando JDK 21 disponível em `/usr/lib/jvm/java-21-openjdk`.
