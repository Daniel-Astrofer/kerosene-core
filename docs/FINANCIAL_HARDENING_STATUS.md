# Financial Hardening Status

Este documento registra o estado tecnico apos a rodada de hardening financeiro. Ele nao declara o backend pronto para dinheiro real.

## Implementado nesta rodada

- Validacao positiva de valores financeiros em DTOs e services criticos, incluindo limite de escala de 8 casas decimais.
- `idempotencyKey` obrigatoria em transacao de ledger, pagamento de payment request, confirmacao/conclusao de payment link, saque on-chain, envio on-chain e pagamento Lightning.
- Persistencia de idempotencia por banco via `ProcessedTransactionEntity`, evitando dependencia exclusiva de Redis para operacoes financeiras mutaveis.
- Remocao de `passphraseHash` de `WalletResponseDTO`.
- Validacao antecipada de `Digest` no `ParanoidSecurityFilter`, antes de chamar o controller, preservando o body para leitura posterior.
- JWT passa a carregar roles persistidas e o filtro de autenticacao popula authorities `ROLE_*`.
- Confirmacao de payment link valida `txid` contra blockchain client, endereco esperado, output, valor esperado e confirmacoes minimas.
- Settlement do monitor deixa de mapear endereco por `passphraseHash` e passa a usar `depositAddress`.
- Regressao de confirmacao em transacao ja liquidada passa a marcar `AUTO_RESOLUTION_PENDING` em vez de voltar silenciosamente para `PENDING`.
- Saques on-chain e pagamentos Lightning passam a reservar idempotencia e compensar o ledger quando o provider falha depois do debito interno.
- Guard rails de producao deixam de bloquear integracoes reais `bitcoin.rpc.enabled` e `btcpay.enabled`, mantendo bloqueio para chaves de mock/dev em producao.
- Base k6 inicial adicionada para smoke de autenticacao, leitura de wallet, leitura de ledger, status de transacao e escrita financeira controlada.
- Payment links passam a ter store JPA primario em `financial.payment_links`; Redis deixa de ser a fonte duravel do link.
- Saidas externas passam a registrar intencao duravel em `financial.external_provider_outbox` antes da chamada ao provider.
- Mutacoes financeiras e eventos de transferencia passam a gerar eventos de auditoria encadeados por hash em `financial.financial_audit_events`.
- Reconciliacao automatica passa a persistir execucoes e issues em `financial.financial_reconciliation_runs` e `financial.financial_reconciliation_issues`.
- Reconciliacao detecta regressao de confirmacoes em transferencias externas e move o item para `AUTO_RESOLUTION_PENDING`.
- Metricas Micrometer `kerosene.financial.*` foram adicionadas para eventos financeiros, issues de reconciliacao e runs de reconciliacao.
- Metricas para rejeicao de validacao financeira e reutilizacao/rejeicao de idempotencia foram adicionadas.
- Regras Prometheus iniciais foram versionadas em `backend/kerosene/observability/prometheus/financial-alerts.yml`.
- SLOs/SLIs financeiros iniciais foram documentados em `backend/kerosene/docs/OBSERVABILITY_SLOS.md`.
- k6 ganhou perfis `smoke`, `1m_day`, `1m_hour` e `concurrency`.

## Riscos restantes

- O outbox de provider e duravel, mas o retry automatico de chamadas externas ainda e conservador: falhas finais ficam em `AUTO_RESOLUTION_PENDING`/issue para evitar reenvio sem nova autorizacao transacional.
- Reorg/regressao apos settlement fica em `AUTO_RESOLUTION_PENDING`; ainda falta reversao contabil automatica completa para todos os cenarios de prova externa.
- Payment links estao duraveis em banco, mas ainda falta migracao/backfill operacional para links antigos que existam somente no Redis em ambientes ja rodando.
- Ainda existem fluxos legados de derivacao/local provider que precisam de revisao operacional antes de producao.
- Nao ha relatorio de carga real que comprove 1M requisicoes por dia, por hora ou simultaneas.
- Ainda falta observabilidade operacional completa de producao: dashboards publicados, regras conectadas ao Prometheus/Alertmanager real, tracing distribuido validado e SLOs aprovados pela operacao.

## Proximas entregas recomendadas

1. Implementar retry automatico de outbox somente com prova de autorizacao renovavel ou token provider idempotente seguro.
2. Automatizar reconciliacao contabil de reversao para reorg/double-spend com workflow de aprovacao.
3. Conectar as regras Prometheus versionadas ao Alertmanager real e criar dashboards Grafana para `kerosene.financial.*`, pool de conexao, Redis, provider e blockchain.
4. Rodar carga real em ambiente equivalente a producao e publicar relatorio com p95/p99, erros, pool de conexao e gargalos.
5. Revisar fluxos legados restantes que usam `findAll`, fallback local de derivacao e comentarios de endpoint publico divergentes da security.
6. Criar runbooks assinados para `AUTO_RESOLUTION_PENDING`, provider failed, reorg, double-spend e divergencia de solvencia.
