# Observability and Financial SLOs

Este documento define a base operacional para monitorar os fluxos financeiros do backend. Ele nao substitui teste de carga real nem conexao com pager em producao.

## Endpoints e coleta

- Actuator expoe `health`, `info`, `metrics` e `prometheus`.
- Metricas financeiras seguem o padrao `kerosene.financial.*` no Micrometer e `kerosene_financial_*` no Prometheus.
- Regras Prometheus versionadas: `backend/kerosene/observability/prometheus/financial-alerts.yml`.
- Runbook de atendimento: `backend/kerosene/docs/RUNBOOK_FINANCIAL_RECONCILIATION.md`.

## SLIs financeiros minimos

| SLI | Fonte | Objetivo inicial |
| --- | --- | --- |
| Rejeicoes de validacao financeira | `kerosene_financial_validation_rejected_total` | Tendencia monitorada; spike deve abrir investigacao. |
| Reutilizacao de idempotencia | `kerosene_financial_idempotency_reused_total` | Replays esperados devem ficar baixos e explicaveis por retry de cliente. |
| Outbox/provider sem dispatch | `kerosene_financial_reconciliation_issue_total{type="PROVIDER_OUTBOX_NOT_DISPATCHED"}` | Zero em operacao normal. |
| Saga/provider incompleta | `kerosene_financial_reconciliation_issue_total{type="PROVIDER_SAGA_INCOMPLETE"}` | Zero em operacao normal. |
| Regressao de confirmacao | `kerosene_financial_reconciliation_issue_total{type="CONFIRMATION_REGRESSION"}` | Zero; qualquer ocorrencia e critica. |
| Runs de reconciliacao com falha | `kerosene_financial_reconciliation_total{outcome="failed"}` | Zero em janela de 5 minutos. |

## SLOs propostos antes de dinheiro real

- 99.9% dos ciclos de reconciliacao devem concluir sem erro em janelas de 30 dias.
- 100% das mutacoes financeiras aceitas devem ter idempotencia persistente e evento de auditoria.
- 100% das regressoes de confirmacao devem gerar `AUTO_RESOLUTION_PENDING`, issue de reconciliacao e alerta critico.
- 100% dos outbox pendentes alem da janela operacional devem gerar issue e alerta.
- 99% das leituras criticas de wallet/ledger devem ficar abaixo do p95 definido no teste de carga do ambiente alvo.

## Lacunas operacionais restantes

- Conectar `financial-alerts.yml` ao Prometheus real e ao Alertmanager/PagerDuty usado pela operacao.
- Publicar dashboards Grafana para `kerosene_financial_*`, Hikari, Redis, blockchain client, provider externo e latencia HTTP.
- Validar tracing distribuido com propagacao entre backend, provider, sidecar MPC e infra de blockchain.
- Publicar relatorio k6/Gatling com p95/p99, taxa de erro, saturacao de pool e limites de conexao.
