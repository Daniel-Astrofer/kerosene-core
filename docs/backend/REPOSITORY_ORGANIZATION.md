# Repository Organization

Este repositorio deve funcionar como monorepo do produto Kerosene, mas a raiz nao deve ser usada como area de cache, build ou rascunhos operacionais.

## Estrutura Canonica

| Caminho | Responsabilidade |
| --- | --- |
| `backend/kerosene` | API principal Spring Boot e assets de deploy específicos do serviço em `deploy/`. |
| `backend/vault` | Servico Vault Java separado. |
| `backend/mpc-sidecar` | Sidecar Go/gRPC para MPC. |
| `backend/adapters` | Adapters/sidecars auxiliares, como Bitcoin Core e Lightning em Python. |
| `backend/tests` | Testes de integracao e apoio compartilhado do backend. |
| `backend/kerosene-infrastructure` | Infraestrutura local/producao atual: Compose, Dockerfiles, Tor, Bitcoin, LND, Vault Raft e observabilidade. |
| `frontend` | App Flutter, web admin e builds de cliente. |
| `scripts` | Entrypoints operacionais mantidos pelo projeto. |
| `docs` | Documentacao tecnica consolidada. |
| `.local` | Arquivo local ignorado pelo Git para rascunhos e snapshots que nao fazem parte do produto. |

## Politica De Limpeza

- Builds e caches gerados nao devem ficar versionados.
- `backend/kerosene/build`, `backend/kerosene/web-admin-build` e `frontend/build/web` podem existir localmente como artefatos atuais, mas nao sao fonte.
- `backend/kerosene/deploy/local` guarda certificados/chaves locais ignorados pelo Git; arquivos versionados de deploy ficam em `backend/kerosene/deploy/{compose,docker,host,postgres,observability,tor}`.
- Diretorios `web-admin-build.stale-*` nao devem ser criados novamente; o build atual substitui o anterior.
- Caches de Gradle, Flutter, Cargo, pytest, IDE e outputs como `target/`, `.dart_tool/`, `.gradle/`, `__pycache__/` e `coverage/` podem ser removidos e recriados pelas ferramentas.
- Scripts temporarios de refatoracao ou diagnostico ficam em `.local/archive/root-one-off` quando precisam ser preservados.

## Regras De Mudanca Estrutural

- Antes de mover servicos oficiais, atualizar scripts, testes, compose e docs no mesmo conjunto de mudancas.
- Evitar duplicar a mesma topologia Docker em dois lugares; `docker-compose.yml` na raiz deve continuar sendo o ponto de entrada local.
- Adapters externos compartilhados por mais de um dominio do backend devem convergir para um pacote comum antes de remover implementacoes duplicadas.
