# Lógica de Negócios do Kerosene

## Visão geral

O backend do Kerosene opera em modo **KFE-only** para domínio financeiro. A lógica central de dinheiro, saldo, carteira, transação, recebimento, PSBT, eventos fiscais, reservas e reconciliação pertence ao `source.kfe`.

O backend amplo não deve possuir módulos financeiros paralelos. Qualquer comportamento financeiro novo deve nascer no KFE ou sob rotas administrativas KFE, como `/api/admin/kfe/**`.

## Motor financeiro oficial

### KFE — Kerosene Financial Engine

**Função:** gerenciar o processamento transacional central, execução por trilhos e estado financeiro auditável.

Responsabilidades:

- carteiras e ciclo de vida de carteira;
- saldos disponíveis, bloqueados e observados;
- transferências internas;
- saques on-chain;
- saques Lightning;
- quote/fee de transação;
- payment requests e receive requests;
- PSBT workflow de cold/watch-only wallet;
- eventos fiscais derivados de transações KFE;
- visão administrativa de reservas;
- auditoria, statement e reconciliação;
- outbox de execução financeira.

## Regras arquiteturais

1. Nenhum pacote fora de `source.kfe` pode implementar domínio financeiro próprio.
2. Rotas financeiras públicas devem usar `/kfe/**`.
3. Rotas financeiras administrativas devem usar `/api/admin/kfe/**`.
4. O retorno de módulos financeiros removidos é bloqueado por `scripts/verify-kfe-only.sh`.
5. Não existe feature flag para voltar ao backend financeiro antigo.

## Módulos removidos

Os antigos domínios financeiros foram expurgados. Eles não são fonte de verdade, não devem ser restaurados e não devem aparecer em código executável.

Use o documento `docs/backend/KFE_ONLY_FINANCIAL_ARCHITECTURE.md` para a política completa de prevenção de regressão.
