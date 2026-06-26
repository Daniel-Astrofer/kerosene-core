# Referência de Infraestrutura do Kerosene

Este documento descreve o backend e o runtime de infraestrutura do Kerosene conforme representado pela documentação atual do repositório, topologia do compose, scripts, Kubernetes e referências de configuração da aplicação. A camada `infra/` é a organização canônica para contratos de Docker, Kubernetes, runtime e scripts; caminhos antigos devem existir apenas como wrappers temporários quando indispensáveis.

## Escopo

Fontes revisadas:

| Fonte | Propósito |
| --- | --- |
| `infra/docker/images.yaml` | Contrato canônico de imagens, tags locais, Dockerfiles e contextos de build durante a migração para `infra/`. |
| `infra/docker/compose/local.compose.yaml` | Topologia local atual com múltiplos shards incluindo shards da aplicação, Postgres, Redis, sidecars MPC, Tor, Vault, serviços Bitcoin, serviços Lightning, Prometheus e web admin. |
| `infra/docker/compose/hardened.compose.yaml` | Topologia distribuída endurecida com shards regionais, serviços ocultos Tor, Vanguards, Vault, MPC, PostgreSQL, Redis e LND. |
| `backend/kerosene/src/main/resources/application*.properties` | Configuração de runtime do backend para perfis default, Docker e produção. |
| `scripts/*.sh` | Scripts de ciclo de vida local, logging, arming do Vault, build do web admin e release. |
| `backend/mpc-sidecar`, `backend/vault` | Serviços de suporte para assinatura MPC e custódia de material sensível. |
| `backend/kerosene/src/main/resources/db/migration` | Histórico do esquema de banco de dados gerenciado pelo Flyway. |

A terminologia legada "Hydra" não é utilizada aqui. Os sistemas atuais são descritos como backend Kerosene, infraestrutura Kerosene, shards regionais, Vault, sidecar MPC, serviços Bitcoin/Lightning e web admin.

## Topologia de Runtime

O Kerosene é implantado como um backend Spring Boot com serviços de suporte para dados, custódia, observabilidade e privacidade de rede. As topologias local e distribuída compartilham a mesma arquitetura central:

- Shards regionais do backend executam a aplicação Spring Boot do Kerosene.
- Cada shard possui suas próprias dependências PostgreSQL e Redis.
- Sidecars MPC fornecem suporte a assinatura via gRPC/TLS.
- Serviços ocultos Tor e Vanguards fornecem exposição de rede privada e endurecimento.
- Serviços Vault custodiam ou coordenam material sensível.
- Bitcoin Core, um indexador e LND suportam fluxos on-chain e Lightning.
- Prometheus e web admin fornecem visibilidade operacional.

### Topologia Local

O comando canônico para inicialização local é:

```bash
bash infra/scripts/local/control.sh start
```

O script utiliza o arquivo compose raiz, prepara o build do web admin Flutter para servir no backend, inicia a infraestrutura local, arma o Vault quando configurado, aguarda o provisionamento da chave mestra do shard e exibe os endereços onion quando disponíveis.

Serviços locais principais:

| Grupo | Serviços | Responsabilidade |
| --- | --- | --- |
| Shards da aplicação | `kerosene-app-is`, `kerosene-app-ch`, `kerosene-app-sg` | Instâncias Spring Boot usando perfis `docker,prod` e dependências específicas do shard. |
| Bancos de dados | `db-is`, `db-ch`, `db-sg` | Instâncias PostgreSQL 17 com volumes específicos do shard. |
| Cache | `redis-is`, `redis-ch`, `redis-sg` | Redis 7 com proteção por senha e persistência AOF. |
| MPC | `mpc-sidecar-is`, `mpc-sidecar-ch`, `mpc-sidecar-sg` | Sidecars Go gRPC para operações MPC/assinatura com TLS. |
| Tor | `kerosene-tor-is`, `kerosene-tor-ch`, `kerosene-tor-sg`, `kerosene-tor-vault` | Serviços ocultos e egresso controlado. |
| Vanguards | `kerosene-vanguards-is`, `kerosene-vanguards-ch`, `kerosene-vanguards-sg` | Endurecimento de circuito Tor. |
| Vault | `kerosene-vault`, `kerosene-vault-arm`, `vault-raft-1..3`, `vault-raft-bootstrap` | Custódia de material sensível e suporte a quorum/bootstrap Raft do Vault. |
| Bitcoin | `bitcoin-core`, `bitcoin-indexer` | Bitcoin Core podado e suporte opcional a indexador. |
| Lightning | `lnd-neutrino`, `lnd-bootstrap` | Nó LND e suporte à inicialização para faturas/pagamentos Lightning. |
| Operações | `web-admin`, `prometheus` | Interface administrativa e coleta de métricas. |

### Topologia Distribuída

`infra/docker/compose/hardened.compose.yaml` modela a implantação distribuída endurecida:

- Um serviço Vault central exposto internamente através do `kerosene-tor-vault`, sem `ports` diretas no host.
- Três shards regionais: `IS`, `CH` e `SG`.
- Limites de rede separados para tráfego de banco de dados, Tor e MPC.
- PostgreSQL com SSL, Redis com proteção por senha, sidecars MPC com mTLS e volumes persistentes específicos do shard.
- Contêineres da aplicação configurados com capacidades Linux reduzidas, `no-new-privileges`, tmpfs e identidade de shard persistente.
- Serviço oculto Tor e serviço Vanguards por shard.

## Runtime do Backend

O backend está em `backend/kerosene` e executa em Java 21 com Spring Boot.

| Área | Comportamento atual |
| --- | --- |
| Binding HTTP | Perfil Docker faz bind para `0.0.0.0`; porta do contêiner é `8080`. |
| Persistência | Modo de validação Hibernate é utilizado; alterações de esquema são gerenciadas por migrações SQL. |
| Migrações | Flyway está desabilitado por padrão e habilitado em produção através de `FLYWAY_ENABLED=true`. |
| Redis | Perfil padrão usa configuração local; perfil Docker usa DNS do serviço compose. |
| Segurança | JWT sem estado, segurança em nível de método, filtragem de requisições paranoid, rate limiting e filtros de autenticação JWT. |
| Validação de requisições | Content-type JSON é exigido para requisições que mutam JSON; limite padrão de payload é `2KB`, rotas PSBT permitem `64KB`. |
| CORS | Origens permitidas são explícitas através de `APP_CORS_ALLOWED_ORIGINS`; configuração de produção com caractere curinga é tratada como inválida. |
| Passkeys/WebAuthn | RP ID padrão local/docker é `kerosene-device`; produção requer configuração explícita. |
| Observabilidade | Actuator health/info/metrics/prometheus além de grupos de saúde liveness/readiness. |

## Superfície da API

A API HTTP está documentada por domínio em [api/README.md](api/README.md). A referência consolidada segue disponível em [API_REFERENCE.md](API_REFERENCE.md) para auditoria e revisão full-text.

| Métrica | Valor |
| --- | --- |
| Entradas extraídas de controllers Spring | `178` |
| Superfícies runtime não-controller | `3` |
| Entradas documentadas por domínio | `181` (`180` REST + `1` WebSocket/STOMP) |
| Seções REST na referência consolidada | `162` (`161` pares método/caminho únicos) |
| Interfaces não controladoras | WebSocket/STOMP e Actuator são documentados junto das superfícies públicas de runtime. |

Endpoints públicos de saúde e release:

| Endpoint | Propósito |
| --- | --- |
| `GET /healthz` | Sonda de saúde de compatibilidade. |
| `GET /health/live` | Sonda de liveness. |
| `GET /health/ready` | Sonda de readiness incluindo dependências críticas. |
| `GET /system/release` | Metadados de release/build. |
| `/actuator/health/**` | Visualizações de saúde do Actuator quando habilitado. |

## Plano de Dados

### PostgreSQL e Migrações

O esquema do banco de dados é gerenciado por migrações Flyway em `backend/kerosene/src/main/resources/db/migration`.

A cobertura de esquema conhecida inclui:

- Usuários, segurança de conta, estado TOTP/passkey/chave-de-dispositivo.
- Carteiras, lançamentos contábeis, saldos, solicitações de pagamento, links de pagamento, intenções de pagamento e transações.
- Contas Bitcoin, carteiras frias, fluxos de trabalho PSBT, solicitações de recebimento, eventos fiscais e estado on-chain.
- Configuração de tesouraria, provas de reserva, solicitações de pagamento, eventos de auditoria e registros de reconciliação.
- Entidades centrais do KFE, idempotência, outbox de execução, dados de hash/raiz de auditoria, estado de rede da carteira, cursores de recebimento, suporte UTXO/PSBT e tokens de dispositivo.

Nota sobre migração: a migração de token de notificação de dispositivo foi corrigida de `V10__notification_device_tokens.sql` para `V10_1__notification_device_tokens.sql` para evitar uma versão duplicada do Flyway.

### Redis

Redis é uma dependência crítica de runtime. Ele suporta:

- Rate limiting de rotas e identidade.
- Estado de desafios de cadastro, recuperação e passkey.
- Idempotência financeira e proteção contra replay.
- Disjuntor (circuit-breaker) e sinais operacionais efêmeros.
- Eventos temporários de contabilidade/histórico utilizados pelos serviços atuais.

A saúde de readiness inclui Redis porque os fluxos de autenticação e financeiros dependem dele.

## Bitcoin, Lightning e KFE

O Kerosene suporta tanto APIs legadas de carteira/transação quanto a superfície de API mais recente do KFE.

Capacidades Bitcoin:

- RPC do Bitcoin Core em fluxos Docker/produção endurecidos.
- Comportamento opcional de modo RPC/padrão para desenvolvimento local.
- ZMQ `rawtx` e `hashblock` quando habilitado.
- Integração opcional com Esplora/indexador.
- Configuração de endereço/xpub de carteira quente e xpub mestre da plataforma.
- Fluxos de trabalho PSBT e configuração de assinantes com quórum por URL/chave de API/id.

Capacidades Lightning:

- LND com TLS e autenticação por macaroon.
- Geração de faturas Lightning e execução de pagamentos.
- Suporte a bootstrap através de serviços compose locais.

Capacidades KFE documentadas na referência da API:

- Criação/lista de carteiras, rotação de endereços, lista de UTXOs e criação de PSBT para carteira fria.
- Envio/recuperação de transações com tratamento de idempotência.
- Descoberta de capacidade de recebimento.
- Agregação de dashboard do usuário.
- Inspeção administrativa de raiz/evento/transação de auditoria.

Status de verificação do KFE: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests 'source.kfe.*'` concluído com `BUILD SUCCESSFUL` em `2026-06-12`.

## Vault e MPC

`backend/vault` é um serviço Java separado construído com Maven. Ele é armado por fluxos HMAC controlados pelo diretor e é responsável pela custódia de material sensível.

`backend/mpc-sidecar` é um serviço Go gRPC definido por `proto/mpc.proto`. No compose ele executa em modo `HARDWARE_ENCLAVE`, requer material de chave mestra e utiliza TLS. O backend integra-se a ele através da configuração `mpc.sidecar.*`.

## Modelo de Segurança

A postura de segurança do backend é em camadas:

| Camada | Controles |
| --- | --- |
| Borda HTTP | Tratamento estrito de content-type, limites de tamanho de payload, verificação opcional de integridade do corpo `Digest: SHA-256=<base64>`, lista de permissões CORS e rate limiting. |
| Identidade | Autenticação via bearer JWT, autorização em nível de método, PIN da aplicação, TOTP, passkeys, códigos de backup e controles de dispositivo/sessão. |
| Segurança financeira | Chaves de idempotência, hashing de requisições, trilhas de auditoria, trabalhadores de reconciliação, provas de reserva, controles de tesouraria e processamento de outbox do provedor. |
| Implantação | Privilégios reduzidos de contêiner, sem portas diretas do Vault no host na topologia endurecida, serviços ocultos Tor, Vanguards, TLS/mTLS quando aplicável e redes/volumes separados por shard. |
| Padrões de produção | Modos mock e fallback são desabilitados em produção para Bitcoin, endereços derivados localmente e aceitação de TXID de voucher. |

## Trabalhadores Agendados

Responsabilidades agendadas/background observadas:

| Área | Responsabilidades |
| --- | --- |
| Preços | Atualização do ticker BTC. |
| Contabilidade e auditoria | Auditoria Merkle, limpeza de histórico, auditoria de reconciliação e auditoria de saldo oculto (shadow balance). |
| Segurança e soberania | Verificações de desvio de tempo (time drift), atestação remota e heartbeat de soberania. |
| Transações | Monitor de liquidez, monitor de transferências recebidas, monitor de transações pendentes, monitor de ativação de conta, reconciliação financeira e processamento de outbox do provedor. |
| Tesouraria | Verificações de integridade financeira e trabalhador de pagamentos da tesouraria. |
| Contas Bitcoin | Retenção, monitor de recebimento, monitor de carteira fria e expiração de PSBT. |
| Pagamentos | Serviços de execução externa e reconciliação. |
| KFE | Outbox de execução, manipulação de log/raiz de auditoria, monitoramento de rede, funções de recebimento/endereço de carteira e suporte a liquidação recebida. |

## Build e Operações

Comandos comuns:

```bash
bash infra/scripts/local/control.sh start
bash infra/scripts/local/control.sh logs
bash infra/scripts/local/control.sh stop
cd backend/kerosene && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test
cd backend/mpc-sidecar && go test ./...
cd backend/vault && mvn package
```

Nota sobre teste/build do backend: `processResources` escreve recursos gerados em `build/generated-resources/main` e realoca saídas legadas obsoletas de `build/resources/main` antes da execução da tarefa. Isso evita que artefatos antigos do web admin, como `Icon-512.png`, bloqueiem testes quando saídas de build mais antigas possuem ACLs restritivas.

Verificações operacionais:

| Verificação | Comando ou endpoint |
| --- | --- |
| Liveness do backend | `GET /health/live` |
| Readiness do backend | `GET /health/ready` |
| Saúde de compatibilidade | `GET /healthz` |
| Slice de teste KFE | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests 'source.kfe.*'` |
| Suite completa de testes do backend | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test` |
| Logs locais | `bash infra/scripts/local/control.sh logs` |

## Segredos e Artefatos

Nunca commitar:

- Arquivos `.env`.
- Certificados, chaves privadas, keystores, chaves Tor, macaroons LND, contas de serviço, segredos do diretor, chaves mestras ou dumps de banco de dados.
- Saída de build do frontend gerada em `frontend/build/**`.
- Volumes compose ou saídas de build copiadas que existem apenas em runtime.
- Estado local sensível de runtime em `infra/runtime/local/**` quando existir no ambiente local.

Nota sobre artefatos do repositório: diretórios `web-admin-build.stale-*` são artefatos gerados históricos. Eles não são fonte da verdade para o comportamento do web admin.

### Correções Recentes do Backend
- **Idempotência e Exceções Globais**: Comportamentos do `GlobalExceptionHandler` para `WalletExceptionsCreation` e `DuplicateTransactionException` foram restaurados para retornar `409 Conflict`, garantindo que as APIs mantenham a idempotência correta sem recorrer a 500.
- **Escalabilidade do Log de Auditoria**: O lock do appender do Log de Auditoria KFE foi alterado de um lock rigoroso de linha no PostgreSQL (`financial_audit_lock` ID 1) para um `pg_advisory_xact_lock(hashtext('GLOBAL_AUDIT_APPENDER'))` muito mais rápido, reduzindo drasticamente o inchaço MVCC e resolvendo problemas graves de gargalo.
- **Processamento do Outbox de Execução**: O `KfeExecutionOutboxProcessor` foi atualizado para tentar novamente em caso de `IllegalStateException`, que anteriormente forçava transações assíncronas a um estado morto em vez de realizar novas tentativas.
