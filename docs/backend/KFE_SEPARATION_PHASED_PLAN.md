# Plano por fases: separação do KFE

**KFE:** Krinse Financial Engine<br>
**Objetivo:** separar o serviço financeiro da Kerosene para que o sistema padrão permaneça responsável por identidade, autenticação, notificações, operação e shell do produto, enquanto o KFE seja o único dono de saldos, carteiras, transações, pagamentos, rails, auditoria financeira e reconciliação.

## Diagnóstico atual

A Kerosene já está parcialmente organizada em torno de KFE:

- O backend possui o domínio `source.kfe` com controllers, DTOs, entidades, repositories, rails, serviços e casos de uso.
- As rotas financeiras ativas já usam `/kfe/**`, `/api/public/kfe/**` e `/api/admin/kfe/**`.
- As tabelas centrais do financeiro vivem no schema `financial`, com migrations como `V12__kfe_core.sql`, `V20__kfe_payment_requests.sql`, `V21__kfe_psbt_workflows.sql` e `V22__kfe_tax_event_classifications.sql`.
- A migration `V23__drop_legacy_financial_tables.sql` remove tabelas financeiras legadas em ambiente dev/test.
- O frontend já consome endpoints KFE em `AppConfig` e em features como contas financeiras, envio, atividade financeira e segurança.

Mas ainda não existe separação real de serviço:

- O backend é um único módulo Gradle/Spring Boot.
- O KFE roda dentro dos mesmos processos `kerosene-app-is`, `kerosene-app-ch` e `kerosene-app-sg`.
- Pacotes fora de `source.kfe` não devem importar classes internas KFE; nesta entrega, os imports `source.kfe.*` em `main`/`test` fora do pacote KFE foram removidos por portas comuns e adaptadores locais.
- `source.kfe` não importa mais `source.auth.*`; as integrações de identidade/autenticação financeira passam por portas comuns com adapters em `source.auth.integration`. As dependências diretas de `source.auth.*`, `source.notification.*` e `source.security.*` foram removidas do KFE por portas comuns. A dependência externa restante `source.sovereign.quorum` em `KfeQuorumGateway` também foi removida por `FinancialQuorumPort`; nesta etapa, `source.kfe` depende apenas de `source.common.*` e de seus próprios pacotes internos.
- O gate `scripts/verify-kfe-only.sh` foi usado como validação da Fase 0 e agora também valida que código fora do KFE não importa `source.kfe.*`; ele deve permanecer obrigatório em CI para impedir retorno ao financeiro legado ou vazamento de implementação KFE.

## Alvo arquitetural

Separar em dois contextos explícitos:

1. **Kerosene Core**
   - Identidade, signup, login, passkeys, PIN, dispositivos, notificações, shell do app, operação geral e controles soberanos não financeiros.
   - Não acessa tabelas financeiras.
   - Não importa classes internas do KFE.
   - Fala com KFE por contrato HTTP/evento/porta.

2. **KFE — Krinse Financial Engine**
   - Carteiras, saldos, transações, payment requests, rails on-chain/Lightning, PSBT, reservas, tax events, auditoria financeira, outbox financeiro, reconciliação e idempotência.
   - É o único source of truth financeiro.
   - Expõe API própria sob `/kfe/**`, `/api/public/kfe/**` e `/api/admin/kfe/**`.
   - Publica eventos mínimos para o Core, sem vazar modelo interno.

## Fase 0 — Fechar o estado KFE-only atual

**Status atual desta entrega:** iniciado e validado localmente. O flag legado foi removido do código executável/testes e `scripts/verify-kfe-only.sh` passa.

**Meta:** garantir que o monólito atual esteja limpo antes de separar fisicamente.

Ações:

- Manter o flag legado `kfe.legacy-financial.enabled` ausente de código executável e testes.
- Fazer `scripts/verify-kfe-only.sh` passar sem exceções.
- Manter os pacotes legados ausentes: `source.ledger`, `source.payments`, `source.wallet`, `source.bitcoinaccounts`.
- Atualizar a documentação antiga para deixar claro que APIs legadas são arquivo histórico, não contrato ativo.

Entregável:

- KFE-only confirmado por script.
- Nenhum fallback financeiro legado.

## Fase 1 — Cortar imports diretos entre Core e KFE

**Status atual desta entrega:** concluído logicamente no monólito. Não há mais imports `source.kfe.*` fora de `source.kfe` em `main`/`test`. O fluxo de signup usa `FinancialWalletProvisioningPort`, notification usa `NotificationAuditPort`, health usa `FinancialRailHealthPort`, production safety usa `FinancialRailProductionSafetyPort`, soberania usa `FinancialAuditIntegrityPort`, admin operações usa `FinancialOperationsAdminPort`, e dev balance usa contrato comum com implementação KFE.

**Meta:** manter tudo no mesmo deploy, mas inverter dependências para contratos.

Ações:

- Criar portas no Core para chamadas financeiras necessárias:
  - `FinancialWalletProvisioningPort` para criação de carteira no signup. **Criada.**
  - `NotificationAuditPort` para auditoria durável de eventos de notificação. **Criada.**
  - `FinancialRailHealthPort` para health de rails financeiros. **Criada.**
  - `FinancialRailProductionSafetyPort` para safety checks de produção dos rails. **Criada.**
  - `FinancialAuditIntegrityPort` para raiz/auditoria usada por telas soberanas. **Criada e migrada para cliente remoto Core → KFE.**
  - `FinancialOperationsAdminPort` para blockchain, lightning, logs e métricas no admin operacional. **Criada.**
  - `DevBalanceInjector` como contrato comum, com implementação KFE. **Criado.**
  - `FinancialAuthorizationPort` para autenticação transacional.
  - `FinancialReservePort` para overview de reservas.
- Mover integrações de dev como `DevBalanceInjector` para dentro do KFE ou para perfil de teste do KFE. **Implementação real movida para `source.kfe.integration.KfeDevBalanceInjector`; contrato comum mantido para consumidores.**
- Substituir imports `source.kfe.*` fora de `source.kfe` por portas/interfaces. **Concluído para `main`/`test` nesta entrega.**
- Criar testes ArchUnit: fora de `source.kfe`, nenhum pacote pode depender de `source.kfe..`. **Criado em `ArchitectureGuardrailsTest.nonKfeProductionCodeDoesNotDependOnKfeImplementationPackages`.**
- Reforçar o gate local/CI `scripts/verify-kfe-only.sh` para bloquear imports `source.kfe.*` fora do pacote KFE. **Criado.**

Entregável:

- `source.auth`, `source.common`, `source.notification`, `source.security` não importam classes internas KFE.
- A comunicação ainda é local, mas já usa contratos.
- Regressões de dependência são bloqueadas por ArchUnit e pelo script `scripts/verify-kfe-only.sh`.

## Validações adicionadas após a Fase 1

Comandos validados localmente com Java 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests source.architecture.ArchitectureGuardrailsTest --no-daemon
scripts/verify-kfe-only.sh
```

Resultado esperado:

- Build do teste arquitetural passa.
- O script informa `OK: non-KFE code does not import KFE implementation packages`.
- O script informa `OK: KFE code does not import auth implementation packages`.
- O script informa `OK: KFE code does not import notification implementation packages`.
- O script informa `OK: KFE code does not import security implementation packages`.
- O script informa `OK: KFE code does not import sovereign implementation packages`.

## Fase 2 — Separar módulos dentro do repositório

**Status atual desta entrega:** avançada para multi-módulo real. Agora existem `:kerosene-contracts`, `:kerosene-shared` e `:kfe-service`. `source.kfe` não importa mais `source.auth.*` em `main`/`test`. O diretório financeiro de usuários usa `source.common.financial.FinancialUserDirectoryPort`, com implementação `source.auth.integration.AuthFinancialUserDirectoryAdapter`; autorização transacional financeira usa `source.common.financial.FinancialTransactionApprovalPort`, com implementação `source.auth.integration.AuthFinancialTransactionApprovalAdapter`. `KfeTransactionWalletResolver`, `KfeTransactionAuthorizationUseCase`, `KfeWalletNetworkService`, `KfeTransactionController` e `KfeDevBalanceInjector` deixaram de depender diretamente de entidades, repositories, exceções e serviços de auth. Também foram extraídas as portas `FinancialNotificationPort`, `FinancialNotificationAuditPort`, `FinancialMpcKeyPort`, `FinancialQuorumPort` e `StringColumnCryptoPort`, removendo dependências KFE para `source.notification.*`, `source.security.*`, `source.sovereign.mpc.*` e `source.sovereign.quorum.*`. A porta `FinancialWalletProvisioningPort` também foi movida de `source.auth...signup.port` para `source.common.financial`; esses contratos foram movidos fisicamente para o módulo `:kerosene-contracts`. Utilitários comuns usados por Core/KFE foram movidos para `:kerosene-shared`, e o pacote `source.kfe` foi movido fisicamente para `:kfe-service`.

**Meta:** transformar separação lógica em separação compilável.

Ações:

- Alterar Gradle para multi-módulo. **Concluído para a primeira separação física: `settings.gradle.kts` inclui `:kerosene-contracts`, `:kerosene-shared` e `:kfe-service`; o app principal depende dos três módulos.**
- Estrutura alvo:
  - `:kerosene-core`
  - `:kfe-service`
  - `:kerosene-contracts`
  - opcional: `:sovereign-services` / `:mpc-client`
- Mover `source.kfe` para o módulo `kfe-service`. **Concluído: `source.kfe` está em `backend/kerosene/kfe-service/src/main/java/source/kfe`.**
- Mover DTOs/contratos compartilhados mínimos para `kerosene-contracts`, evitando expor entidades JPA. **Concluído para portas financeiras: `source.common.financial.*` e `source.common.security.StringColumnCryptoPort` vivem em `:kerosene-contracts`.**
- Separar migrations: Core não deve executar migrations financeiras; KFE executa apenas migrations do schema financeiro.
- Manter removidos os imports `source.auth.*` dentro de `source.kfe`. **Concluído e protegido por `ArchitectureGuardrailsTest.kfeProductionCodeDoesNotDependOnAuthImplementationPackages` e `scripts/verify-kfe-only.sh`.**
- Manter removidos os imports `source.notification.*` e `source.security.*` dentro de `source.kfe`. **Concluído e protegido por ArchUnit e `scripts/verify-kfe-only.sh`.**
- Extrair a fronteira restante `source.sovereign.quorum` para uma porta comum `FinancialQuorumPort`. **Concluído com implementação `source.sovereign.integration.SovereignFinancialQuorumAdapter`.**
- Separar testes por módulo e manter um pacote de testes de contrato. **Iniciado: `:kfe-service:test`, `:kerosene-contracts:build` e `:kerosene-shared:build` passam; o script bloqueia imports indevidos nos módulos.**

Entregável:

- O módulo `:kerosene-contracts` compila isolado, sem Spring/JPA/implementações. **Concluído.**
- O módulo `:kerosene-shared` compila isolado e não depende de pacotes de implementação. **Concluído.**
- O módulo `:kfe-service` compila/testa isolado e contém `source.kfe`. **Concluído para o primeiro corte físico.**
- O Core compila usando contratos/shared/KFE como dependências externas ao app principal. **Concluído para o primeiro corte físico.**
- O Core compila sem o módulo KFE como dependência interna.
- O KFE compila sem depender de controllers ou detalhes de aplicação do Core.

### Fronteira KFE pronta para multi-módulo

Após os cortes preparatórios, não há imports diretos de `source.auth.*`, `source.notification.*`, `source.security.*` ou `source.sovereign.*` dentro de `source.kfe` em `main`/`test`. O quorum soberano foi encapsulado por `FinancialQuorumPort`, com implementação `source.sovereign.integration.SovereignFinancialQuorumAdapter`. Isso deixa o KFE pronto para iniciar a movimentação Gradle/multi-módulo com fronteira clara: `kfe-service` depende de `kerosene-contracts/common`, enquanto adapters Core/Sovereign/Notification vivem fora do KFE.

## Fase 3 — Separar runtime e infraestrutura

**Status atual desta entrega:** avançada. O runtime separado do KFE foi preparado com imagem Docker própria, manifests Kubernetes próprios e overlay Docker Compose local opcional. Os primeiros cutovers Core → KFE foram iniciados: `FinancialWalletProvisioningPort`, `FinancialRailHealthPort` e `FinancialAuditIntegrityPort` agora têm clientes remotos no Core e endpoints internos no KFE. O workload separado ainda usa o executável Spring Boot atual com perfil/role `kfe-service` enquanto os demais ports são migrados para HTTP/mensageria. O Core agora exclui `source.kfe` do component scan/JPA scan padrão, e o KFE é importado por auto-configuração apenas quando o profile `kfe` está ativo. No Kubernetes, o `web-page` passa a encaminhar `/kfe/**`, `/api/public/kfe/**` e `/api/admin/kfe/**` para `Service/kfe-service`, com NetworkPolicies, secrets, importação de imagem e overlays locais ajustados para tratar KFE como workload obrigatório.

**Meta:** rodar KFE como serviço próprio, ainda no mesmo cluster local/prod.

Ações:

- Criar imagem/container `kfe-service` separado de `kerosene-app-*`. **Caminho canônico: `infra/docker/images/kfe-service/Dockerfile`.**
- Adicionar `kfe-service-is`, `kfe-service-ch`, `kfe-service-sg` ao compose/k8s. **Iniciado com manifests Kubernetes `kfe-service` e overlay Compose local `infra/docker/compose/local.kfe.compose.yaml` usando shards WVO/IW5/LTV.**
- Definir variáveis próprias: banco, Redis/outbox, Bitcoin Core, LND, MPC sidecar, Vault e políticas de release. **Iniciado via `kfe-service-config` e variáveis `KEROSENE_RUNTIME_ROLE=kfe-service`/`SPRING_PROFILES_ACTIVE=prod,kfe` ou `docker,kfe`.**
- Colocar Core → KFE atrás de cliente HTTP interno ou mensageria confiável. **Iniciado: `FinancialWalletProvisioningPort`, `FinancialRailHealthPort` e `FinancialAuditIntegrityPort` usam clientes remotos no Core e chamam endpoints `/internal/kfe/**`.**
- Garantir autenticação serviço-a-serviço: mTLS, token assinado ou assinatura HMAC rotacionável. **Iniciado com credencial compartilhada em header interno para o primeiro endpoint; ainda deve evoluir para mTLS/HMAC rotacionável.**
- Separar healthchecks: `/health` do Core não pode mascarar falha financeira; KFE expõe saúde financeira própria. **Preparado no workload KFE com probes próprios em `/health/ready` e `/actuator/health/liveness`; ainda falta endpoint financeiro dedicado no código.**

Entregável:

- `kfe-service` possui imagem e workload próprios renderizando em Kustomize. **Concluído para local/staging/production e para os overlays locais `local-full`/`local-ha`.**
- Overlay Docker Compose local `kfe-split` renderiza serviços KFE por shard. **Concluído para simulação local.**
- Core sobe sem inicializar beans KFE. **Preparado: `Application` exclui `source.kfe` do component scan e limita EntityScan/JpaRepositories ao Core; o profile `kfe` também exclui controllers Core do executável compartilhado; falta validar boot runtime com banco real.**
- KFE sobe e processa financeiro independentemente. **Preparado: `KfeServiceRuntimeConfiguration` é auto-configurada apenas com profile `kfe`, e Kubernetes roteia tráfego financeiro para `kfe-service`; falta validação de boot runtime completo.**
- Falha do KFE degrada apenas fluxos financeiros, não login/shell do produto. **Parcial: onboarding financeiro já chama KFE remotamente; ainda falta degradar com política explícita de retry/fallback e migrar os demais ports.**


### Extensão planejada: P2P seller-owned payment providers

Foi criado o desenho `docs/backend/KFE_P2P_SELLER_PAYMENT_PROVIDERS.md` para um subdomínio P2P dentro do KFE. A proposta permite que sellers conectem provedores próprios, como Mercado Pago, para receber fiat diretamente na conta deles, enquanto o KFE verifica status, valor, moeda, recebedor e referência por APIs/webhooks do provider antes de avançar a ordem.

Essa extensão deve permanecer dentro do `:kfe-service` e não deve vazar para o Core. O padrão recomendado é OAuth/conta conectada; API key manual deve ser fallback controlado e auditável.

## Fase 4 — Separar dados e ownership operacional

**Meta:** impedir que o Core toque dados financeiros mesmo por acidente.

Ações:

- Dar ao Core usuário de banco sem permissão no schema/tabelas financeiras.
- Dar ao KFE usuário de banco proprietário do schema financeiro.
- Avaliar duas opções:
  - curto prazo: mesmo Postgres por shard, schema e usuário separados;
  - médio prazo: banco KFE separado por shard.
- Mover outbox financeiro, locks, idempotência e auditoria para ownership exclusivo do KFE.
- Criar migração/backfill de produção somente se houver dado real; a migration destrutiva atual deve permanecer marcada como dev/test.

Entregável:

- Permissões comprovam isolamento.
- KFE é o único escritor financeiro.
- Core consulta apenas APIs/eventos do KFE.

## Fase 5 — Atualizar frontend e admin para fronteira explícita

**Meta:** a UI entende que financeiro é um serviço separado.

Ações:

- Separar clients de API:
  - `CoreApiClient` para auth/notificações/perfil.
  - `KfeApiClient` para carteiras/transações/payment requests/reservas/auditoria financeira.
- Manter rotas públicas KFE para QR/link de pagamento.
- No web-admin, separar telas operacionais Core de telas financeiras KFE.
- Tratar indisponibilidade do KFE com estado visual específico, não como falha geral do app.

Entregável:

- Frontend não mistura endpoints financeiros e Core em um único client sem fronteira.
- Admin deixa claro o que pertence ao KFE.

## Fase 6 — Gates finais e corte de produção

**Meta:** permitir corte seguro sem regressão financeira.

Ações:

- Gates obrigatórios em CI:
  - `scripts/verify-kfe-only.sh`
  - ArchUnit de dependências Core/KFE
  - testes de contrato Core ↔ KFE
  - testes de idempotência financeira
  - testes de auditoria append-only/Merkle root
  - testes de degradação quando KFE está indisponível
- Rodar modo shadow: Core chama KFE real, mas compara eventos críticos com logs/auditoria existentes.
- Fazer cutover por ambiente: local → staging → shard piloto → demais shards.
- Remover adaptadores temporários após estabilização.

Entregável:

- KFE é serviço separado em build, deploy, runtime, dados e contrato.
- Core não possui código financeiro interno.
- Rollback é operacional, não retorno ao financeiro legado.

## Ordem recomendada de execução

1. Corrigir o gate `verify-kfe-only.sh`. **Concluído nesta entrega.**
2. Introduzir portas e remover imports `source.kfe.*` fora do KFE. **Concluído logicamente no monólito nesta entrega.**
3. Adicionar guardrails ArchUnit. **Concluído para Core → KFE, KFE → auth, KFE → notification, KFE → security e KFE → sovereign.**
4. Separar Gradle em módulos. **Avançado com `:kerosene-contracts`, `:kerosene-shared` e `:kfe-service`.**
5. Separar containers/deploy. **Iniciado com Dockerfile KFE, manifests Kubernetes e Compose overlay local.**
6. Separar permissões de banco.
7. Separar frontend/admin clients.
8. Fazer cutover com testes de contrato e degradação.

## Critério de pronto

A separação está completa quando estas afirmações forem verdadeiras:

- O Core compila sem importar `source.kfe..`. **Validado e formalizado em ArchUnit/script.**
- O KFE compila sem importar `source.auth..`, `source.notification..`, `source.security..` ou `source.sovereign..`. **Validado e formalizado em ArchUnit/script.**
- O módulo `:kerosene-contracts` compila isolado e não importa `source.kfe..`, `source.auth..`, `source.notification..`, `source.security..`, `source.sovereign..`, Spring ou JPA. **Validado por `scripts/verify-kfe-only.sh`.**
- O módulo `:kerosene-shared` compila isolado e não importa pacotes de implementação. **Validado por `scripts/verify-kfe-only.sh`.**
- O módulo `:kfe-service` compila/testa isolado e o app principal compila consumindo-o como dependência. **Validado por Gradle.**
- O workload Kubernetes `kfe-service` renderiza nos overlays local/staging/production. **Validado por `kubectl kustomize`.**
- O boundary de runtime Core/KFE é protegido por teste arquitetural: Core exclui KFE do scan padrão e KFE publica auto-configuração profile-gated. **Validado por `ArchitectureGuardrailsTest`.**
- Os primeiros clientes remotos Core → KFE (`FinancialWalletProvisioningPort`, `FinancialRailHealthPort` e `FinancialAuditIntegrityPort`) são cobertos por testes unitários no Core, e seus endpoints internos KFE são cobertos por testes no módulo `:kfe-service`. **Validado por Gradle.**
- O overlay Docker Compose local para KFE separado renderiza com perfil `kfe-split`. **Validado por `docker compose config` com valores dummy de secrets obrigatórios.**
- O KFE é iniciado, testado e versionado como serviço próprio.
- Somente o KFE tem permissão de escrita no schema financeiro.
- Todas as rotas financeiras ativas passam por `/kfe/**`, `/api/public/kfe/**` ou `/api/admin/kfe/**`.
- Não existe flag de retorno ao financeiro legado.
- O app continua autenticando e abrindo o shell mesmo com KFE indisponível.
- Operações financeiras falham de forma segura, auditável e idempotente quando KFE ou rails externos estão indisponíveis.
