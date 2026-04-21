# Infrastructure

## Visao geral

O backend foi preparado para rodar tanto como uma aplicacao Spring isolada quanto como parte de um cluster distribuido com shards regionais, Vault central e transporte via Tor.

Arquivos de referencia principais:

- `Dockerfile`
- `docker-compose.yml`
- `../kerosene-infrastructure/docker-compose.local.yml`

## Topologias disponiveis

### 1. Execucao local simples

Sem profile adicional, o backend sobe com:

- PostgreSQL local
- Redis local
- propriedades de `src/main/resources/application.properties`

Este modo serve para desenvolvimento da aplicacao em uma unica instancia.

### 2. Topologia distribuida em `docker-compose.yml`

O compose do repositorio modela um cluster com:

- `1` Vault central: `kerosene-vault`
- `1` Tor para o Vault: `kerosene-tor-vault`
- `3` shards regionais de app: `kerosene-app-is`, `kerosene-app-ch`, `kerosene-app-sg`
- `3` Postgres regionais: `db-is`, `db-ch`, `db-sg`
- `3` Redis regionais: `redis-is`, `redis-ch`, `redis-sg`
- `3` MPC sidecars: `mpc-sidecar-is`, `mpc-sidecar-ch`, `mpc-sidecar-sg`
- `3` Tor sidecars de app: `kerosene-tor-is`, `kerosene-tor-ch`, `kerosene-tor-sg`
- `3` instancias de Vanguards: `kerosene-vanguards-is`, `kerosene-vanguards-ch`, `kerosene-vanguards-sg`

Regioes explicitamente nomeadas no compose:

- `IS` (Iceland)
- `CH` (Switzerland)
- `SG` (Singapore)

### 3. Simulacao full-cluster local

O arquivo `../kerosene-infrastructure/docker-compose.local.yml` simula o cluster completo em uma unica maquina e documenta o fluxo recomendado de bootstrap local.

Esse arquivo e especialmente util para entender:

- ordem de subida do cluster
- imagens customizadas fora deste repositorio
- diferencas entre simulacao local e topologia de producao

## Componentes de runtime

### Aplicacao Spring (`kerosene-app-*`)

Cada shard executa uma instancia do backend com:

- `SPRING_PROFILES_ACTIVE=docker`
- conexao propria com Postgres e Redis regionais
- endereco do `MPC_SIDECAR_HOST`
- caminhos para socket Tor, onion do Vault e estado do Vanguards
- `cap_drop: ALL` e apenas `IPC_LOCK` liberado
- `tmpfs` para material sensivel e arquivos temporarios

### PostgreSQL (`db-*`)

Cada shard tem um Postgres dedicado:

- imagem `postgres:17-alpine` no compose deste repositorio
- TLS habilitado com certificados montados em `/certs`
- init scripts em `docker-entrypoint-initdb.d`
- volume persistente proprio por regiao

### Redis (`redis-*`)

Cada shard possui um Redis dedicado:

- senha obrigatoria
- `appendonly yes`
- volume persistente proprio por regiao

### MPC sidecar (`mpc-sidecar-*`)

Cada shard possui um sidecar separado para funcoes de MPC/custodia:

- runtime definido no repositorio irmao `../mpc-sidecar`
- storage sensivel apoiado em `tmpfs`
- integrado com o backend por host/porta configuravel

### Vault + Tor do Vault

O Vault central e tratado como o ponto de provisionamento da chave mestra:

- o servico `kerosene-vault` nao expoe porta para o host
- o acesso ocorre via `kerosene-tor-vault`
- o backend descobre o endereco onion por arquivo (`VAULT_ONION_FILE`)
- o acesso e feito via proxy Tor por socket (`VAULT_PROXY_PATH`)

### Tor sidecars + Vanguards

Cada shard usa um sidecar Tor para:

- ingress/egress via hidden services
- isolamento de rede
- sockets locais para trafego do app

Cada shard tambem possui um `vanguards` sidecar para protecao adicional de circuitos Tor.

## Redes Docker

Redes definidas no `docker-compose.yml`:

- `net_db_is`
- `net_db_ch`
- `net_db_sg`
- `net_vault`
- `net_mpc`
- `net_tor`
- `tor_egress`

Leitura pratica:

- banco e redis ficam isolados por regiao
- o Vault tem rede propria
- sidecars MPC compartilham `net_mpc`
- trafego Tor usa `net_tor`/`tor_egress`

## Volumes persistentes

Volumes observados:

- Postgres: `pg_data_is`, `pg_data_ch`, `pg_data_sg`
- Redis: `redis_data_is`, `redis_data_ch`, `redis_data_sg`
- Tor keys/data/socket/control: `tor_keys_*`, `tor_data_*`, `tor_socks_*`, `tor_control_*`
- estado do Vanguards: `vanguards_state_*`
- onion do Vault: `tor_keys_vault`

Isso indica uma separacao clara entre:

- estado persistente de dados
- identidade/rede Tor
- material operacional que deve viver em RAM (`tmpfs`)

## Build e runtime do container

O `Dockerfile` atual usa duas etapas:

1. build com `eclipse-temurin:21-jdk-jammy`
2. runtime com `gcr.io/distroless/java21-debian12`

Caracteristicas importantes:

- build por `./gradlew bootJar -x test`
- runtime distroless e `nonroot` (`UID 65532`)
- `EXPOSE 8080` apenas para rede interna do container
- profile padrao do container: `SPRING_PROFILES_ACTIVE=docker`

## Hardening observado

No codigo e na compose aparecem varias camadas de endurecimento:

- `cap_drop: ALL` e apenas `IPC_LOCK` onde necessario
- `no-new-privileges:true`
- `tmpfs` para dados sensiveis e caminhos volateis
- `distroless` para reduzir superficie do runtime
- Vault sem publish de portas
- egress documentado para ser bloqueado no SO com `iptables` + `seccomp`
- filtros HTTP paranoicos e suppressao de headers identificadores

## Ingress, egress e exposicao de portas

O desenho desejado e:

- a JVM escuta `0.0.0.0:8080` dentro do container
- nao ha exposicao publica direta da app pelo compose
- entrada e mediada por Tor hidden services
- Vault e resolvido por onion, nao por IP publico

No nivel da aplicacao:

- `actuator` expoe apenas `health`
- ha endpoints de soberania para status e telemetria
- WebSocket fica sob `/ws/**`

## Dependencias externas de infraestrutura

A infraestrutura do backend depende tambem de sistemas fora deste repositorio:

- `../vault`
- `../mpc-sidecar`
- provedores HTTP de custodia e Lightning
- provedores de onramp
- fontes externas de dados Bitcoin/fiat

## Sequencia de bootstrap em alto nivel

A sequencia operacional inferida do codigo e da compose e:

1. Postgres/Redis/MPC/Tor/Vault sobem.
2. A app sobe no profile `docker`.
3. `VaultBootstrapCoordinator` tenta atestar o no e buscar a chave mestra.
4. A chave vai para `MasterKeyMemoryStore` em RAM.
5. `RemoteAttestationService` e `SovereigntyHeartbeatService` passam a monitorar a integridade do no.
6. Schedulers de ledger, transacoes, auditoria e liquidez entram em operacao.

## Observabilidade e operacao

Mecanismos disponiveis diretamente no backend:

- `GET /actuator/health`
- `GET /sovereignty/status`
- `GET /sovereignty/telemetry`
- `GET /sovereignty/ping`
- logging HTTP com mascaramento de campos sensiveis
- MDC por request com `requestId`, `method`, `path`, `userId` e `service`

## O que esta dentro e o que esta fora deste repositorio

Dentro deste repositorio:

- aplicacao Spring Boot
- Dockerfile da app
- compose do cluster do backend
- scripts de deploy/init de banco e rede

Fora deste repositorio, mas parte do runtime total:

- imagens e codigo do Vault
- sidecar MPC
- compose de simulacao completa do cluster
