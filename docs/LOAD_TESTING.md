# Load Testing

Este diretorio contem uma base de teste k6 para validar comportamento operacional antes de qualquer declaracao de capacidade.

O script atual nao prova suporte a 1M requisicoes. Ele cobre cenarios de autenticacao, leitura de wallet, leitura de ledger, consulta de status de transacao, leitura de transferencias externas e escrita financeira interna com idempotencia obrigatoria. A escrita fica desativada por padrao para evitar movimentacao acidental de saldo.

## Pre-requisitos

- Backend Kerosene rodando com banco e dependencias reais do ambiente alvo.
- Usuario e wallet de teste isolados de qualquer saldo real.
- JWT valido para endpoints autenticados.
- k6 instalado localmente ou disponivel no runner de CI.

## Smoke sem escrita financeira

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e JWT="$JWT" \
  backend/kerosene/load-tests/k6/financial-smoke.js
```

## Smoke com autenticacao

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_USERNAME="$LOGIN_USERNAME" \
  -e LOGIN_PASSWORD="$LOGIN_PASSWORD" \
  -e LOGIN_TOTP_CODE="$LOGIN_TOTP_CODE" \
  backend/kerosene/load-tests/k6/financial-smoke.js
```

## Smoke com escrita financeira controlada

Use somente em ambiente isolado, com wallets de teste e saldo descartavel.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e JWT="$JWT" \
  -e ENABLE_WRITES=true \
  -e WALLET_SENDER=Main \
  -e WALLET_RECEIVER=Receiver \
  -e LEDGER_AMOUNT=0.00000001 \
  -e IDEMPOTENCY_PREFIX="ci-$(date +%s)" \
  backend/kerosene/load-tests/k6/financial-smoke.js
```

## Perfil 1M por dia

1M por dia equivale a aproximadamente 11,6 requisicoes por segundo. Use o perfil abaixo como execucao longa, com ambiente descartavel e metricas externas ligadas.

```bash
k6 run \
  -e PROFILE=1m_day \
  -e BASE_URL=http://localhost:8080 \
  -e JWT="$JWT" \
  -e DURATION=24h \
  -e RATE=12 \
  backend/kerosene/load-tests/k6/financial-smoke.js
```

## Perfil 1M por hora

1M por hora equivale a aproximadamente 278 requisicoes por segundo. Este perfil exige sizing real de banco, Redis, JVM, pool de conexao e providers.

```bash
k6 run \
  -e PROFILE=1m_hour \
  -e BASE_URL=http://localhost:8080 \
  -e JWT="$JWT" \
  -e DURATION=1h \
  -e RATE=278 \
  -e PREALLOCATED_VUS=500 \
  -e MAX_VUS=2000 \
  backend/kerosene/load-tests/k6/financial-smoke.js
```

## Perfil de concorrencia

Concorrencia nao e o mesmo que volume agregado. Use este perfil para medir VUs simultaneos em leitura sem inferir automaticamente capacidade financeira de escrita.

```bash
k6 run \
  -e PROFILE=concurrency \
  -e BASE_URL=http://localhost:8080 \
  -e JWT="$JWT" \
  -e VUS=1000 \
  -e DURATION=10m \
  backend/kerosene/load-tests/k6/financial-smoke.js
```

## Como evoluir para validacao de capacidade

1. Rodar contra ambiente com Postgres, Redis e providers externos equivalentes ao perfil de producao.
2. Separar cenarios de leitura e escrita para medir p95/p99 sem contaminar saldos de teste.
3. Executar `PROFILE=1m_day`, depois `PROFILE=1m_hour`, e nunca inferir 1M simultaneas sem teste dedicado de concorrencia.
4. Capturar CPU, memoria, conexoes do pool, latencia do banco, erros de provider, metricas de idempotencia e metricas de settlement.
5. Promover o script para CI/CD somente com dados sinteticos e ambiente descartavel.

## Limite atual

A existencia deste script e apenas a base de validacao. O backend ainda precisa de execucoes reais de carga, relatorios com metricas, limites de SLO, tune de pool/conexao e analise de gargalos antes de qualquer afirmacao de suporte a alto volume.
