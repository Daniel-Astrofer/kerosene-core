# Runbook: Financial Reconciliation

Este runbook define o tratamento autonomo para `AUTO_RESOLUTION_PENDING`, regressao de confirmacoes, suspeita de double-spend e falha de provider.

## Sinais monitorados

- `financial.financial_reconciliation_runs`: cada ciclo automatico de reconciliacao.
- `financial.financial_reconciliation_issues`: issues abertas por divergencia.
- `financial.external_provider_outbox`: intencoes de chamada a provider que nao chegaram a `DISPATCHED`.
- `financial.network_transfer_events`: eventos operacionais por transferencia.
- `financial.financial_audit_events`: trilha imutavel encadeada por hash.
- Metricas Prometheus `kerosene.financial.reconciliation`, `kerosene.financial.reconciliation_issue` e `kerosene.financial.network_transfer_event`.

## Severidade

- `CRITICAL`: regressao de confirmacoes, possivel reorg/double-spend, saldo creditado ou liquidado com prova on-chain regredida.
- `HIGH`: outbox/provider sem estado terminal, provider falhou apos reserva/debito, transferencia presa em `PROVIDER_PENDING`.
- `MEDIUM`: atraso de confirmacao, provider sem payload suficiente, divergencia ainda sem impacto contabil.

## Procedimento para `AUTO_RESOLUTION_PENDING`

1. Bloquear automaticamente novas mutacoes relacionadas ao `transfer_id` afetado.
2. Consultar `financial_reconciliation_issues` pelo `transfer_id`.
3. Consultar `network_transfer_events` e `financial_audit_events` pelo mesmo aggregate/reference.
4. Validar o `txid` pela fonte blockchain configurada e por fallback automatico quando disponivel.
5. Comparar endereco, output, valor, confirmacoes e status de replacement/double-spend.
6. Se houve regressao real depois de settlement, manter o saldo em estado rastreavel e executar reversao contabil automatica quando a politica permitir.
7. Se provider falhou sem broadcast confirmado, manter compensacao e exigir nova autorizacao self-service do usuario para reenvio.
8. Registrar decisao tecnica em evento auditavel saneado, sem payload financeiro completo.

## Procedimento para outbox/provider failed

1. Verificar se `external_provider_outbox.status` esta `FAILED_FINAL` ou `FAILED_RETRYABLE`.
2. Confirmar se o ledger foi compensado quando houve debito previo.
3. Consultar provider por `provider_reference`, `txid` ou `payment_hash`, se existir.
4. Nao reenviar automaticamente usando payload antigo se a autorizacao transacional nao puder ser renovada.
5. Para novo envio, exigir nova `idempotencyKey` e nova autorizacao do usuario.

## Procedimento para reorg/double-spend

1. Manter a transferencia em `AUTO_RESOLUTION_PENDING`.
2. Abrir issue `CONFIRMATION_REGRESSION` ou equivalente.
3. Comparar a transacao em pelo menos duas fontes blockchain.
4. Se a transacao reaparecer com confirmacoes suficientes, registrar reconciliacao positiva antes de liberar.
5. Se a transacao foi substituida ou perdeu output/valor esperado, iniciar reversao contabil conforme politica automatica ou mover para `USER_ACTION_REQUIRED`.

## Criterio de encerramento

Um issue so deve ser fechado quando houver:

- evidencia blockchain/provider anexada;
- decisao tecnica ou acao self-service registrada;
- auditoria imutavel gerada;
- saldo reconciliado ou explicitamente marcado para reversao;
- ausencia de outbox pendente para a mesma operacao.
