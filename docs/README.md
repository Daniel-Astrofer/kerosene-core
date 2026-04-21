# Kerosene Backend Documentation

Esta pasta consolida a documentacao do servico backend Kerosene a partir do codigo atual do repositorio.

Fontes usadas nesta leitura:

- `src/main/java`
- `src/main/resources`
- `Dockerfile`
- `docker-compose.yml`
- `../kerosene-infrastructure/docker-compose.local.yml`

Data de revisao desta documentacao: `2026-04-18`.

## Resumo executivo

Kerosene e um backend `Java 21` com `Spring Boot 3.3.2` organizado como um monolito modular. Hoje o servico expoe:

- `71` endpoints HTTP mapeados por controllers
- `4` endpoints STOMP/WebSocket
- `15` jobs agendados com `@Scheduled`
- persistencia principal em `PostgreSQL`
- estado efemero e controles operacionais em `Redis`
- integracoes de runtime com `Vault`, `MPC sidecar`, `Tor`, provedores de custodia/lightning/onramp e fontes de dados Bitcoin

## Mapa da documentacao

- [ARCHITECTURE.md](ARCHITECTURE.md): visao macro da aplicacao, modulos, fluxos, estado e mecanismos transversais.
- [INFRASTRUCTURE.md](INFRASTRUCTURE.md): topologia Docker, componentes de runtime, redes, volumes, hardening e operacao.
- [CONFIGURATION.md](CONFIGURATION.md): perfis Spring, variaveis de ambiente, propriedades criticas e guard-rails de producao.
- [API_REFERENCE_CONTROLLERS.md](API_REFERENCE_CONTROLLERS.md): referencia detalhada da API HTTP baseada nos controllers e handlers atuais.

## O que o servico faz

Em termos de negocio e plataforma, o backend cobre os seguintes blocos:

- autenticacao, onboarding, recovery, TOTP e passkeys/WebAuthn
- carteiras (`wallet`) e perfis de endereco
- ledger interno, saldo, historico e payment requests
- transacoes on-chain, Lightning e pagamentos externos
- transparencia, proof-of-reserves e auditoria Merkle
- vouchers e fluxos de onboarding pagos
- marketplace/alocacao de mineracao
- notificacoes e atualizacoes realtime por WebSocket
- soberania operacional, attestation, telemetria e bootstrap de chave via Vault

## Mapa rapido do codigo

Os principais pacotes em `src/main/java/source` sao:

- `auth`: autenticacao, passkeys, recovery, perfis de seguranca
- `wallet`: carteira e dominio associado
- `ledger`: ledger interno, auditoria e eventos
- `transactions`: pagamentos externos, on-chain, Lightning, onramp e monitoramento
- `treasury`: reserva, solvencia, fee policy e configuracao financeira
- `voucher`: onboarding com voucher e links publicos
- `mining`: catalogo de rigs e alocacoes
- `notification`: envio de eventos para usuarios
- `security`: vault, attestation, heartbeat, telemetry, honeypot e defesa operacional
- `config` e `common`: configuracao transversal, WebSocket, logging e suporte tecnico

## Como ler

Se voce esta chegando agora no servico, a ordem recomendada e:

1. Leia [ARCHITECTURE.md](ARCHITECTURE.md) para entender os modulos e fluxos.
2. Leia [INFRASTRUCTURE.md](INFRASTRUCTURE.md) para entender a topologia de execucao.
3. Leia [CONFIGURATION.md](CONFIGURATION.md) para levantar ambientes com as propriedades corretas.
4. Use [API_REFERENCE_CONTROLLERS.md](API_REFERENCE_CONTROLLERS.md) como consulta de contrato HTTP.

## Observacoes importantes

- Esta documentacao foi derivada do codigo, nao de uma especificacao OpenAPI gerada automaticamente.
- O build atual nao mostra integracao OpenAI no backend; por isso esta pasta documenta a aplicacao em si, nao um subsistema OpenAI.
- Ha arquivos de infraestrutura em repositorios irmaos (`../vault`, `../mpc-sidecar`, `../kerosene-infrastructure`) que fazem parte do runtime total do cluster.
