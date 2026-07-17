# Plano de Execução — Liquidação Binária, Lightning e Core On-chain

**Documento vivo.** Atualizar a cada PR/entrega.  
**Versão:** 1.0  
**Criado:** 2026-07-16  
**Fonte normativa:**

- Documento de arquitetura: *Motor KFE — Liquidação Binária, Gestão de Liquidez e Mitigação de Riscos* (v1.0, Jul/2026)
- Adendo operacional: política de armazenamento do nó on-chain (2026-07-16)
- Princípios de engenharia: Clean Code, SOLID, Clean/Hexagonal Architecture, DDD, ledger, segurança, observabilidade
- Estado real do código em `backend/kerosene/kfe-service` + adapters + infra

**Objetivo:** implementar cada camada com coesão, sem pular pré-requisitos, e sem “meia liquidação”.  
**Regra de progresso:** só marcar `DONE` quando houver código + testes + observabilidade mínima + entry no checklist da seção 9.

---

## 0. Como usar este documento

| Status | Significado |
|--------|-------------|
| `DONE` | Implementado, coberto por testes, alinhado ao doc |
| `PARTIAL` | Existe base útil, mas incompleta ou com buracos |
| `NOT_STARTED` | Não existe no código |
| `DOUBT` | Decisão de produto/infra pendente — **não codificar como se estivesse resolvido** |
| `BLOCKED` | Depende de item anterior ou decisão externa |

**Ao fechar uma entrega:**

1. Atualizar a matriz da §3 (status + commit/PR + data).
2. Marcar o item do checklist da §9.
3. Registrar dúvidas novas em §4.
4. Não inventar flags/providers “para o futuro” (YAGNI).

**Princípio de coesão:** cada fase fecha um *slice vertical* testável (domínio → aplicação → rail → infra → teste), não “só interface” ou “só LND”.

---

## 1. Princípios de código (obrigatórios em todo PR)

### 1.1 Clean Code / KISS / DRY / YAGNI

- Valores financeiros: **sempre `long` sats** (ou `BigDecimal` só se for cotação FX de *display*, nunca saldo).
- Proibido `double`/`float` em cálculo de saldo, fee, liquidez, PoR.
- Funções pequenas; um use case por responsabilidade.
- Sem comentários que apenas repetem o nome do método.
- Não copiar validação de saldo/liquidez em 30 lugares — portas/serviços únicos.
- Não implementar gestão de canal “bonita” se a liquidação binária e o inbound Lightning ainda não fecham.

### 1.2 SOLID (mapeado ao KFE)

| Princípio | Aplicação no KFE |
|-----------|------------------|
| S | `KfeSubmitTransactionUseCase` orquestra; flags de validação em portas/serviços separados; execução rail em `KfeRailExecution` |
| O | Novo rail/provider = novo adapter + registro no registry, sem reescrever o motor |
| L | Gateways `Lightning*`/`Onchain*` substituíveis sem quebrar o outbox |
| I | Manter `LightningInvoiceGateway` ≠ `LightningPaymentGateway` ≠ `LightningClient` (saldo/status) |
| D | Domínio/aplicação dependem de portas; LND/BTCPay/Core ficam em `rail/` e adapters Python |

### 1.3 Arquitetura alvo (coesa com o monólito atual)

```
controller / dto
        ↓
application (use cases: submit, settlement, authorization)
        ↓
domain model + balance/ledger rules (sats, estados binários de negócio)
        ↓
ports (interfaces em rail/ + common.financial)
        ↓
adapters (LND REST, lightning_flask, Bitcoin Core, BTCPay, MPC sidecar)
        ↓
infra (Postgres, Redis opcional, outbox worker, ZMQ, metrics)
```

- Domínio **não** importa HTTP LND, Flask, Spring Security details.
- Integração Core Kerosene continua via `source.common.financial.*` (já separado).

### 1.4 Modelagem financeira (invariantes)

1. **Atomicidade binária:** resultado final da operação de negócio é `SETTLED` (1) ou `FAILED`/rollback (0).  
   Estados `EXECUTING` / `REQUIRES_RECONCILIATION` são **processuais** (assíncronos), não “meio satoshi”.
2. **Unidade atômica:** sats inteiros.
3. **Desconfiança mútua:** KFE ↔ LND/MPC/Core — prova positiva antes de liberar/confirmar.
4. **Reservas provadas:** `totalAssetsBtc ≥ totalOperationalExposureBtc` antes e depois (quando PoR ativo).
5. **Recuperação:** crash → rollback ACID ou retomada via outbox/idempotência, sem perda/duplicação.

### 1.5 Ledger

- Não atualizar saldo “solto”: sempre movimento (`RESERVE`, `SETTLE_DEBIT`, `CREDIT_*`, etc.) + saldo derivado.
- Créditos de available com unique constraint por tipo/tx (já em V37).
- Auditoria append-only + hash chain/Merkle onde já existir no KFE.

---

## 2. Foto do estado atual (baseline 2026-07-16)

### 2.1 O que já existe e é sólido

| Área | Onde | Notas |
|------|------|-------|
| KFE-only financial SoR | `kfe-service`, docs `KFE_ONLY_*` | Legado proibido; gate `verify-kfe-only.sh` |
| Rails enum | `KfeRail`: INTERNAL, ONCHAIN, LIGHTNING | OK |
| Submit + idempotência DB | `KfeSubmitTransactionUseCase`, `KfeTransactionIdempotencyUseCase` | Reserva de chave antes do intent |
| Reserve/lock saldo | `KfeBalanceService.reserve` + movements | Lock de available → locked |
| Outbox execução | `KfeExecutionOutbox*`, workers | Retry em falhas retryable |
| Onchain outbound | `KfeOnchainOutboundExecutor` + PSBT gateway | Broadcast ≠ settle (correto) |
| Onchain inbound / conf | monitores, ZMQ, balance sync, payment request onchain | Relativamente maduro |
| Dual-ledger cold/custodial | `BALANCE_CONTRACT.md` | Contrato de `observed` vs `available` |
| Lightning **pay** LND REST | `LndRestLightningClient` | `/v2/router/send`; flag `lightning.lnd.rest.enabled` default **false** |
| Lightning outbound settle | `KfeLightningOutboundExecutor` | Chama payment gateway e settle |
| Invoice gateway (parcial) | BTCPay / Configurable | **Não** LND nativo no KFE |
| Adapter Python LND | `backend/adapters/lightning_flask` | Status, invoice, pay, cohesion |
| Quorum/MPC boundary | `KfeQuorumGateway` + ports | Revalidação via portas; profundidade VLS a validar |
| Separação módulo/runtime | multi-module + k8s kfe-service | Avançado; não é blocker da liquidação |

### 2.2 Lacunas críticas vs documento de arquitetura

| Lacuna | Impacto |
|--------|---------|
| Receive Lightning desligado no produto | `KfeWalletNetworkService`: `boolean lightning = false` → sempre `KFE_LIGHTNING_RECEIVE_NOT_CONFIGURED` |
| Payment request LIGHTNING não emite bolt11 | Cria request sem invoice LND |
| `LndRestLightningClient` **não** implementa `LightningInvoiceGateway` | Inbound depende de BTCPay/configurable |
| Flags binárias (V_*) não são porta explícita + log forense por flag | Validação existe espalhada; não é a “porta AND” auditável do doc |
| Sem `pg_advisory_xact_lock` explícito no submit | Concorrência via row lock/`FOR UPDATE` em balance — **validar equivalência** |
| Sem reserva de **liquidez Lightning** (V_LIQUIDEZ) até HTLC | Risco check-then-act de canal |
| Sem circuit breaker global de liquidez de saída | Pode aceitar saque sem liquidez de rede |
| Sem V_NO_JAMMING / limites HTLC / denylist peer | Gestão de risco LN ausente |
| Gestão de canais | `PARTIAL` (decision+LND+worker; Loop opcional) |
| Redis SETNX de idempotência | Só Postgres hoje; doc pede Redis + fallback DB |
| Débito definitivo vs reserve-until-proof | Outbound LN settle no success do pay; onchain espera conf — alinhar com “prova HTLC” |
| Política nó full-from-now (adendo) | Infra ainda `BITCOIN_PRUNE_MB` / `bitcoin-pruned-node` |
| Testes adversários 100 saques / jam / Redis down | Não sistematizados |

---

## 3. Matriz mestre de requisitos (rastreamento)

Atualizar colunas **Status / Evidência / Data** a cada entrega.

### 3.1 Invariantes e liquidação binária (doc §1–§2)

| ID | Requisito | Status | Evidência no código | Próximo passo |
|----|-----------|--------|---------------------|---------------|
| INV-01 | Atomicidade 0/1 no resultado de negócio | `PARTIAL` | Status machine + outbox; estados intermediários processuais | Formalizar contrato: EXECUTING ≠ 0.5 sat; documentar + testes de crash |
| INV-02 | Apenas sats inteiros | `PARTIAL` | long sats no domínio; checar quotes/display | Grep gate + teste ArchUnit/proibido double em package financeiro |
| INV-03 | Desconfiança mútua KFE↔rail↔MPC | `PARTIAL` | Outbox + quorum port; LND confiado no pay result | Prova de invoice amount; revalidação MPC policy rigorosa |
| INV-04 | PoR: assets ≥ exposure | `PARTIAL` | Reserve overview admin; sem gate pré-exec obrigatório | Implementar `V_RESERVA_MAT` no submit path |
| INV-05 | Recuperação sem perda/duplicação | `PARTIAL` | Idempotência + outbox + ACID | Chaos tests; lock starvation timeouts |
| V-IDEMPOTENCIA | SETNX Redis + persistência + fallback DB | `PARTIAL` | Gate flag + DB unique | Redis opcional; DB SoT |
| V-LOCK-BANDO | Lock na mesma TX do débito | `PARTIAL` | `requireForUpdate` como V_LOCK_BANDO no gate | Advisory lock se testes exigirem |
| V-ATOMICIDADE | amount+fee inteiros | `DONE` | Gate + validator | — |
| V-SALDO-DISP | Saldo ≥ totalDebit sob lock | `DONE` | Gate sob FOR UPDATE + reserve | Testes 100 threads ainda abertos |
| V-DINHEIRO-REAL | Sem injeção fake em prod | `PARTIAL` | Gate: fail se prod+allow-simulated | Expandir checks de bootstrap |
| V-LIQUIDEZ | Liquidez saída + lock até HTLC | `PARTIAL` | Flag no gate; beta-pass / enforce | Implementar reservation real |
| V-P2P | Webhook assinado + poll | `PARTIAL` | Flag NOT_APPLICABLE no gate | Fase P2P |
| V-ASSINATURA-MPC | Sidecar revalida policy | `PARTIAL` | Gate chama quorum | Policy fields rigorosos |
| V-RESERVA-MAT | Simulação PoR pré-exec | `PARTIAL` | Flag; por default NOT_ENFORCED | PoR global |
| V-NO-JAMMING | HTLC limit + denylist | `PARTIAL` | Flag beta-pass/enforce | LND metrics |
| V-CIRCUIT-BREAKER | Limiar liquidez global | `PARTIAL` | Flag beta-pass/enforce | Metric + reject |
| PORTA-AND | Produto de flags; log cada flag | `PARTIAL` | `BinarySettlementGate` + `KFE_SETTLEMENT_GATE` | Completar liquidez real (Fase 5) |

### 3.2 Gestão de canais (doc §3)

| ID | Requisito | Status | Próximo passo |
|----|-----------|--------|---------------|
| CH-OPEN | Abertura com flags capital/taxa/ancora/MPC/denylist | `PARTIAL` | Decision+LND open; admin API; **dead-man capacity controller** enqueue OPEN on liquidity stress |
| CH-REBAL | Rebal/swap com lucro matemático + profit wallet | `PARTIAL` | Decision+durable queue V44; Loop/submarine provider later |
| CH-CLOSE | Close cooperativo / anchors / freeze high fee | `PARTIAL` | Decision+LND close; capacity controller auto-close inactive |
| CH-PPM | Drain Deterrent / PPM dinâmico | `PARTIAL` | Decision+chanpolicy |
| CH-COST | Custos só da SYSTEM_PROFIT | `PARTIAL` | requireProfitWalletId on structural ops |
| CH-DEADMAN | Capacidade reativa a uso/receita sem admin | `PARTIAL` | `KfeChannelCapacityController` + V45 jobs; preferred-peers config required for auto-open |

### 3.3 Resiliência a falhas (doc §4)

| ID | Cenário | Status | Notas |
|----|---------|--------|-------|
| R-DEVICE | Clique + offline | `PARTIAL` | Idempotência + outbox; FE deve reconsultar estado |
| R-POD | Crash mid-validation | `PARTIAL` | ACID rollback; falta proof com advisory lock formal |
| R-LND | Nó down / DDoS | `PARTIAL` | Retry outbox; **VLS Nível 4** `DOUBT` (LND macaroon vs VLS real) |
| R-PEER | Force-close | `NOT_STARTED` | Anchors policy em infra LND |
| R-EXTERNAL | Timeout provider | `PARTIAL` | Outbox retries; P2P poll depois |

### 3.4 Superfícies de ataque (doc §5–§6)

| Prioridade | Ameaça | Status mitigação |
|------------|--------|------------------|
| CRÍTICA | Race saldo/liquidez | `PARTIAL` (lock+reservation; 100-thread unit) |
| CRÍTICA | Bypass MPC | `PARTIAL` |
| ALTA | Jamming/griefing | `PARTIAL` |
| ALTA | Replay idempotência | `PARTIAL` (DB bom; Redis opcional) |
| MÉDIA | Spoof webhook P2P | `PARTIAL` (desenho) |
| MÉDIA | Force-close griefing | `NOT_STARTED` (anchors) |

### 3.5 Checklist obrigatório do doc §7 (espelho operacional)

| # | Item | Status |
|---|------|--------|
| 1 | Fluxo liquidação em uma TX + lock | `PARTIAL` |
| 2 | Reserva liquidez até HTLC | `DONE` |
| 3 | MPC revalida independente | `PARTIAL` |
| 4 | Limites HTLC + denylist | `PARTIAL` (limits/denylist; stuck monitor residual) |
| 5 | Circuit breaker liquidez | `DONE` |
| 6 | Anchors 100% canais + RBF controlado | `NOT_STARTED` / `DOUBT` (config LND) |
| 7 | Log imutável por flag 0/1 | `DONE` |
| 8 | Reserva on-chain mín. + reverse-swap | `NOT_STARTED` |
| 9 | Testes adversários | `PARTIAL` (unit concurrency) |
| 10 | Replicação síncrona estado LN | `DOUBT` (infra LND/VLS) |

### 3.6 Adendo nó on-chain (2026-07-16)

| ID | Requisito | Status | Evidência |
|----|-----------|--------|-----------|
| NODE-01 | Parar prune de blocos **novos** | `DONE` | `BITCOIN_PRUNE_MB=0` default → `prune=0` |
| NODE-02 | Não ressincronizar histórico já podado | `DONE` | Sem reindex forçado de histórico |
| NODE-03 | Nome/serviço `bitcoin-pruned-node` | `PARTIAL` | Nome legado; renomear com cuidado |
| NODE-04 | Capacidade de disco + alertas | `PARTIAL` | `bitcoin-node-storage-alerts.yml` (node_exporter) |

### 3.7 Lightning produto (fora do PDF mas necessário para “implementar Lightning”)

| ID | Requisito | Status |
|----|-----------|--------|
| LN-IN-01 | Emitir invoice (bolt11) no receive/payment request | `PARTIAL` (LND invoice + payment request; testnet E2E open) |
| LN-IN-02 | Poll/settle inbound com prova amount+hash | `PARTIAL` (monitor PR Lightning + network monitor outbox) |
| LN-OUT-01 | Pagar invoice com fee limit + timeout | `PARTIAL` (status matrix + fail-closed) |
| LN-OUT-02 | Settle only on terminal success; fail unlock | `PARTIAL` (SUCCEEDED only; in-flight → markUnknown) |
| LN-CAP-01 | `canReceiveLightning=true` quando live | `DONE` (invoice gateway isLive + INTERNAL wallet) |
| LN-PROV-01 | Provider preferencial LND invoice+pay | `DONE` (auto: lnd first) |
| LN-ADAPTER-01 | KFE→LND direto | `DONE` (D1=A) |

### 3.8 Core on-chain a melhorar

| ID | Tema | Status | Foco |
|----|------|--------|------|
| OC-01 | Outbound PSBT + conf monitor | `PARTIAL` | Ambiguous execution, fee, stuck |
| OC-02 | Inbound custodial credits | `PARTIAL` | min confs, idempotência crédito |
| OC-03 | Cold observe + ZMQ policy | `PARTIAL` | já documentado em BALANCE_CONTRACT |
| OC-04 | Payment request onchain monitor | `PARTIAL` | |
| OC-05 | System funds/profit wallets | `PARTIAL` | |
| OC-06 | Full-blocks-from-now storage | `DONE` (entrypoint) | adendo |

---

## 4. Dúvidas abertas (resolver antes de codificar a camada)

Registrar decisões com data. Enquanto `DOUBT`, a fase que depende fica `BLOCKED`.

| ID | Dúvida | Opções | Impacto se adiar | Decisão |
|----|--------|--------|------------------|---------|
| D1 | KFE fala com LND **direto** ou via **lightning_flask**? | A) REST LND no KFE B) Flask facade C) ambos | Arquitetura adapter | **A (2026-07-16):** LND REST nativo no KFE (`LndRestLightningClient`); flask permanece ops/opcional |
| D2 | Custódia LN pooled ou por usuário? | Pooled vs per-user | Modelo de saldo | **Pooled (2026-07-16):** hot node da plataforma; saldo spendable do user = INTERNAL ledger |
| D3 | VLS real vs LND macaroon? | VLS N4 vs macaroon | R-LND | *pendente* (beta: macaroon; VLS = hard gate prod institucional) |
| D4 | Idempotência Redis vs DB? | Redis+SETNX vs Postgres | Multi-pod | **DB SoT (2026-07-16):** unique/idempotency table; Redis opcional depois |
| D5 | Advisory lock além de FOR UPDATE? | Sim / não | Race | **Row lock (2026-07-16):** `requireForUpdate` = V_LOCK_BANDO; reavaliar se testes 100 threads falharem |
| D6 | Debitar no pay success vs HTLC proof? | Settle no success LND | Alinhar doc | **Settle no success terminal do pay (2026-07-16);** in-flight = EXECUTING/retry; fail = unlock |
| D7 | Canal management na beta? | YAGNI | Escopo | **Fase 6 pós liquidação (2026-07-16)** |
| D8 | BTCPay invoice provider? | Manter / remover | Config | *pendente* (auto: lnd primeiro quando live) |
| D9 | Prune policy envs | | Disco | *pendente* |
| D10 | P2P no trilho binário agora? | Separar | Escopo | **Fase própria (2026-07-16)** |

---

## 5. Ordem de execução (fases coesas)

Cada fase = slice vertical + critérios de aceite + atualização da matriz §3.

```
Fase 0  Foundations & contratos
   ↓
Fase 1  Binary settlement gate (flags + audit)
   ↓
Fase 2  On-chain core harden (sem LN ainda se necessário)
   ↓
Fase 3  Lightning outbound production-safe
   ↓
Fase 4  Lightning inbound (invoice + settle + capabilities)
   ↓
Fase 5  Liquidez, circuit breaker, jamming defenses
   ↓
Fase 6  Channel lifecycle (open/rebal/close/PPM) — YAGNI até 3–5 estáveis
   ↓
Fase 7  Adendo full-blocks-from-now + ops
   ↓
Fase 8  Adversarial tests + observabilidade + go-live gates
```

Dependências: **não** iniciar Fase 6 antes de 3–5.  
Fase 7 (storage) pode rodar em paralelo com 3–5 (infra).  
Fase 2 pode paralelizar com 1 se não tocar no mesmo gate.

---

## 6. Detalhamento por fase

### Fase 0 — Foundations e contratos de domínio

**Meta:** linguagem única, sem double, estados claros, doc de decisão D1–D6 mínimos.

**Trabalho:**

1. Documento de decisões (este arquivo §4 preenchido para D1, D2, D4, D6).
2. Contrato de estados de `KfeTransactionStatus` vs atomicidade binária (comentário de domínio + testes).
3. Gate estático: proibir `double`/`float` em packages `source.kfe` financeiros (test ou script).
4. Inventário de providers Lightning/onchain e properties defaults seguros (prod fail-closed).
5. Alinhar nomenclatura: `BinarySettlementGate`, `LiquidityReservation`, etc. (nomes estáveis).

**Aceite:**

- [x] D1, D2, D4, D5, D6, D7 decididos e escritos aqui
- [ ] Nenhum double em path de saldo/fee (grep limpo)
- [ ] README curto em `docs/kfe/` linkando este plano + BALANCE_CONTRACT

**Status fase:** `PARTIAL` (decisões fechadas; gate de double e README ainda abertos)

---

### Fase 1 — Porta lógica binária de liquidação

**Meta:** um único pipeline AND de flags com log forense; sem mudar rails ainda se possível.

**Design (Clean + SOLID):**

```
SubmitTransaction
  → BinarySettlementGate.evaluate(Command)  // retorna FlagResult map + pass/fail
  → se fail: rollback, audit flags=0
  → se pass: reserve balance, enqueue outbox / settle internal
```

**Flags mínimas nesta fase (implementar de verdade):**

| Flag | Implementação mínima |
|------|----------------------|
| V_IDEMPOTENCIA | existente DB |
| V_ATOMICIDADE | amount/fee long > 0 rules |
| V_SALDO_DISP | reserve sob lock |
| V_DINHEIRO_REAL | profile/prod safety port |
| V_ASSINATURA_MPC | quorum port (já) |
| V_RESERVA_MAT | stub **fail-open only in non-prod** ou skip se PoR service ainda não — preferir implementável |

**Flags stub explícitos (retornam 1 com `reason=NOT_APPLICABLE` até Fase 5):**  
`V_LIQUIDEZ`, `V_NO_JAMMING`, `V_CIRCUIT_BREAKER`, `V_P2P` — **nunca silenciar em prod** se a operação for Lightning: em prod, Lightning outbound **exige** V_LIQUIDEZ real (logo Lightning prod só após Fase 5) **ou** policy documentada de “beta limited”.

**Trabalho de código sugerido:**

- `application/settlement/BinarySettlementGate.java`
- `application/settlement/SettlementFlag.java` + `FlagEvaluation`
- `KfeAuditLogService` evento `KFE_SETTLEMENT_GATE` com mapa de flags
- Integrar em `KfeSubmitTransactionUseCase` sem inchá-lo (S do SOLID)

**Aceite:**

- [x] Toda submit gera audit com cada flag 0/1 (`KFE_SETTLEMENT_GATE`, REQUIRES_NEW)
- [x] Teste unitário: qualquer flag 0 → gate rejeita (sem reserve no use case)
- [ ] Teste concorrência: N submits paralelos, no máximo 1 debit por saldo

**Status fase:** `PARTIAL` (código + unit tests; concorrência 100 threads e liquidez real pendentes)

**Código entregue (2026-07-16):**

- `source.kfe.application.settlement.BinarySettlementGate`
- `SettlementFlag`, `FlagEvaluation`, `SettlementGateCommand`, `SettlementGateResult`, `SettlementGateRejectedException`
- Integração em `KfeSubmitTransactionUseCase.validateQuoteAndQuorum`
- Audit event `KFE_SETTLEMENT_GATE`
- Props: `kfe.settlement.lightning.risk-gate-mode`, `por-gate-enabled`, `allow-simulated-balances`

---

### Fase 2 — Endurecer core on-chain

**Meta:** caminho onchain confiante (custodial + cold + payment request) antes de escalar LN.

**Trabalho:**

1. Revisar `KfeOnchainOutboundExecutor` + confirmation monitor: estados FAILED vs REQUIRES_RECONCILIATION.
2. Garantir crédito inbound único (V37) em todos os caminhos (ZMQ, monitor, payment request, custodial deposit).
3. Fee estimate + preflight PSBT consistentes com quote.
4. Ambiguous broadcast → never double-spend user balance.
5. Indexes/queries de monitores (performance).
6. Métricas: outbound pending age, inbound credit lag, observed drift.

**Aceite:**

- [ ] Testes de conf 0→N e reorg policy documentada
- [ ] Smokes balance (`run-balance-smokes.sh`) verdes
- [ ] Nenhum crédito available duplicado sob retry

**Status fase:** `PARTIAL` (base existe)

---

### Fase 3 — Lightning outbound production-safe

**Meta:** pagar bolt11 de forma idempotente, com fee limit, timeout, e settle/fail corretos.

**Trabalho:**

1. Fechar D1 (adapter path).
2. Completar `LightningPaymentGateway` live check + fail-closed se `!isLive()`.
3. Normalizar status LND (SUCCEEDED/FAILED/IN_FLIGHT) → KFE binary outcomes.
4. In-flight: manter `EXECUTING` + reconsulta (não settle, não fail definitivo cedo).
5. Validar amount da invoice vs command (anti manipulação).
6. Integração com BinarySettlementGate (Fase 1).
7. Properties: enable LND só com macaroon+URL; tests com fake client.
8. Opcional: usar `lightning_flask` se D1=B.

**Aceite:**

- [ ] Pay success → settle debit + audit
- [ ] Pay fail → unlock reserve + FAILED
- [ ] Pay unknown/timeout → REQUIRES_RECONCILIATION ou retry outbox, **sem** double pay
- [ ] Testes com LND fake + status matrix

**Status fase:** `PARTIAL`

---

### Fase 4 — Lightning inbound (produto completo)

**Meta:** usuário recebe LN; capabilities e payment requests funcionam.

**Trabalho:**

1. `LndRestLightningClient` (ou Flask client) implementa `LightningInvoiceGateway`:
   - create invoice
   - get status
   - cancel (se suportado)
2. Registrar no `ExternalRailProviderConfiguration` (prioridade `lnd` em auto).
3. `KfePaymentRequestService`: se rail LIGHTNING → emitir bolt11, persistir payment_hash, expiry.
4. `KfeWalletNetworkService`: `canReceiveLightning` quando gateway live + policy.
5. Inbound settlement: amount exact, hash match, single credit.
6. Migration se faltar colunas (payment_hash, bolt11 ciphertext?, expires).
7. Monitor já parcial em `KfeNetworkMonitor` — fechar buracos.

**Aceite:**

- [ ] Create payment request LIGHTNING retorna bolt11
- [ ] Pagamento simulado → available credit 1x
- [ ] Capabilities sem `KFE_LIGHTNING_RECEIVE_NOT_CONFIGURED` em env live
- [ ] Testes unitários + integração fake LND

**Status fase:** `NOT_STARTED` (monitor parcial)

---

### Fase 5 — Liquidez, circuit breaker, anti-jamming

**Meta:** V_LIQUIDEZ, V_CIRCUIT_BREAKER, V_NO_JAMMING reais.

**Trabalho:**

1. Modelo `LiquidityReservation` (sats, channel/peer opcional, txId, until HTLC resolve).
2. No gate: checar local balance / outbound capacity; reservar atomico.
3. Circuit breaker config: `kfe.lightning.min-outbound-sats`.
4. HTLC pending count via LND; denylist peers (config/DB).
5. Métricas + alertas Prometheus.
6. Drain Deterrent pode ser **config estática** de PPM nesta fase (automação full = Fase 6).

**Aceite:**

- [ ] Sem liquidez → flag 0, zero débito
- [ ] Liquidez reservada liberada em fail e em success
- [ ] Abaixo do limiar global → rejeita ou enfileira (definir policy)
- [ ] Testes de corrida na reserva de liquidez

**Status fase:** `NOT_STARTED`

---

### Fase 6 — Gestão de canais (doc §3)

**Meta:** open/rebal/close/PPM com porta AND e custo na profit wallet.

**YAGNI:** só após Fase 3–5 estáveis em testnet.

**Trabalho:** domínio `ChannelPolicy`, jobs, integração LND, MPC para funding PSBT onchain, auditoria.

**Aceite:** cada decisão 0/1 auditada; fundos de custo só SYSTEM_PROFIT.

**Status fase:** `PARTIAL` (decisions + LND open/close/ppm + admin API; rebalance execution queued)

**Código (2026-07-16):**

- `application/channel/KfeChannelDecisionService`
- `service/KfeChannelLifecycleService`
- `rail/LightningChannelGateway` + LND impl
- `controller/KfeChannelAdminController`
- migration `V43__channel_operation_decisions.sql`

---

### Fase 7 — Adendo storage Bitcoin (full blocks from now)

**Meta:** desativar prune de blocos futuros; não backfill.

**Trabalho:**

1. `bitcoind-entrypoint.sh`: modo `BITCOIN_PRUNE_MB=0` ou `BITCOIN_BLOCKS_RETENTION=full-from-start-height`.
2. Documentar: gap histórico aceito; `txindex` policy.
3. Compose/k8s volumes e sizing.
4. Alertas de disco.
5. Renomear serviço (opcional, breaking): `bitcoin-node` vs `bitcoin-pruned-node`.
6. Runbook ops.

**Aceite:**

- [ ] Novos blocos retidos
- [ ] Sem redownload forçado do passado
- [ ] Alerta disco em prod

**Status fase:** `NOT_STARTED`  
**Paralelo:** sim (infra)

---

### Fase 8 — Testes adversários, observabilidade, go-live

**Trabalho:**

1. 100 saques simultâneos (saldo + liquidez).
2. Falha Redis (se usado) mid-flow.
3. LND timeout / partial response.
4. Force-close simulation (staging).
5. Dashboards: gate fail rates, outbox lag, LN capacity, PoR.
6. Checklist §7 100% `DONE` ou waivers assinados.
7. Smoke testnet end-to-end: internal → onchain → lightning in/out.

**Status fase:** `NOT_STARTED`

---

## 7. Mapa de pacotes / arquivos-alvo (evitar dispersão)

| Camada | Pacote / path | Conteúdo novo esperado |
|--------|---------------|------------------------|
| Application settlement | `source.kfe.application.settlement` | Gate, flags, command/result |
| Application transaction | já existe | Orquestra gate; não engordar |
| Domain liquidity | `source.kfe.model` + repo | LiquidityReservation entity |
| Rail LN | `source.kfe.rail` | Invoice no LND client; status normalizer |
| Service LN | `source.kfe.service` | PaymentRequest LN, capabilities, liquidity service |
| Adapter Python | `backend/adapters/lightning_flask` | Se D1=B, cliente Java HTTP |
| Infra BTC | `infra/runtime/bitcoin` | prune policy |
| Docs | `docs/kfe/` | este plano, runbooks |
| Tests | `kfe-service/src/test` | unit + concurrency + fake LND |

**Não criar** segundo ledger fora de `source.kfe`.  
**Não** reintroduzir `source.ledger` / legacy.

---

## 8. Critérios de coesão entre camadas (definition of done global)

Uma feature só está pronta quando:

1. **Domínio:** regra em sats + estado binário de negócio.
2. **Aplicação:** use case fino; gate de flags se for liquidação.
3. **Porta:** interface estável.
4. **Adapter:** implementa porta; fail-closed se misconfigured.
5. **Persistência:** movement/audit/outbox consistentes.
6. **Teste:** unit do domínio + pelo menos um teste do caminho feliz e um de falha.
7. **Obs:** log estruturado + métrica se path crítico.
8. **Doc:** status atualizado neste arquivo.

Se faltar um item, status máximo = `PARTIAL`.

---

## 9. Checklist de progresso (marcar com data)

### Foundations
- [x] Fase 0 parcial (decisões) — 2026-07-16
- [x] Decisões D1 D2 D4 D5 D6 D7 — 2026-07-16

### Liquidação
- [x] BinarySettlementGate + audit flags — 2026-07-16
- [x] Concurrency tests saldo — 2026-07-16
- [ ] V_RESERVA_MAT real ou waiver — ____-__-__

### On-chain
- [x] Outbound ambiguous path (PSBT) already present — 2026-07-16 verified
- [ ] Inbound credit unique all paths — ____-__-__
- [ ] Balance smokes green — ____-__-__

### Lightning
- [x] Outbound status matrix — 2026-07-16
- [x] Invoice gateway LND — 2026-07-16
- [x] Payment request bolt11 — 2026-07-16
- [x] Capabilities LN live — 2026-07-16
- [ ] Inbound settle 1x end-to-end testnet — ____-__-__

### Liquidez / risco LN
- [x] Liquidity lock até HTLC (reservation row V42) — 2026-07-16
- [x] Capacity probe + circuit breaker floor — 2026-07-16
- [x] HTLC limits + denylist (`KfeLightningJammingGuard`) — 2026-07-16

### Canais
- [x] Open/rebal/close/PPM binary decisions + admin API — 2026-07-16
- [x] Durable rebalance queue + profit capacity check — 2026-07-16
- [x] Automated drain detection job (PPM + enqueue) — 2026-07-16
- [x] Circular rebalance worker (LND self-payment) — 2026-07-16
- [x] Loop/submarine fallback (`LightningLoopClient`) — 2026-07-16

### Infra
- [x] Full blocks from now (BITCOIN_PRUNE_MB=0 default) — 2026-07-16
- [x] Disk alerts — 2026-07-16

### Go-live
- [x] Checklist doc §7 major items code-complete — 2026-07-16
- [x] Adversarial suite (unit concurrency saldo+liquidez) — 2026-07-16
- [x] Testnet E2E runbook documented — 2026-07-16
- [ ] Testnet E2E executed on live LND — ____-__-__

---

## 10. Ordem de PRs sugerida (pequenos, revisáveis)

| PR | Título | Fase | Risco |
|----|--------|------|-------|
| PR-01 | docs: execution plan + decisions stub | 0 | baixo |
| PR-02 | settlement: BinarySettlementGate + audit | 1 | médio |
| PR-03 | onchain: credit uniqueness & ambiguous path tests | 2 | médio |
| PR-04 | lightning: payment status normalizer + fail-closed | 3 | médio |
| PR-05 | lightning: LND invoice gateway | 4 | alto |
| PR-06 | lightning: payment request + capabilities | 4 | alto |
| PR-07 | liquidity reservation + circuit breaker | 5 | alto |
| PR-08 | jamming limits | 5 | médio |
| PR-09 | bitcoin: disable prune from now | 7 | ops |
| PR-10 | tests: adversarial concurrency | 8 | médio |
| PR-11+ | channel management (se aprovado) | 6 | alto |

Cada PR deve atualizar §3 e §9 deste documento.

---

## 11. Anti-padrões (rejeitar em review)

1. Debitar available sem movement row.
2. `double` em sats.
3. Confiar em status LND sem normalizar + reconsulta.
4. `canReceiveLightning=true` hardcoded sem `isLive()`.
5. Implementar open-channel UI antes de inbound/outbound estáveis.
6. Cache Redis de saldo available sem invalidação.
7. Feature flag que reativa legado financeiro.
8. Settlement 0.5 (deixar locked órfão sem monitor).
9. Invoice amount diferente do valor creditado.
10. “Só loga e segue” em falha de gate em produção.

---

## 12. Referências rápidas de código

| Tema | Path |
|------|------|
| Submit | `kfe-service/.../application/transaction/KfeSubmitTransactionUseCase.java` |
| Balance | `.../service/KfeBalanceService.java` |
| Outbox | `.../service/KfeExecutionOutboxProcessor.java` |
| LN pay | `.../rail/LndRestLightningClient.java` |
| LN out | `.../service/KfeLightningOutboundExecutor.java` |
| LN in monitor | `.../service/KfeNetworkMonitor.java` |
| Providers | `.../rail/ExternalRailProviderConfiguration.java` |
| Capabilities | `.../service/KfeWalletNetworkService.java` |
| Payment requests | `.../service/KfePaymentRequestService.java` |
| Balance contract | `docs/kfe/BALANCE_CONTRACT.md` |
| Binary settlement gate | `kfe-service/.../application/settlement/BinarySettlementGate.java` |
| Adapter LN | `backend/adapters/lightning_flask/` |
| Bitcoin entrypoint | `infra/runtime/bitcoin/bitcoind-entrypoint.sh` |
| Separação KFE | `docs/backend/KFE_SEPARATION_PHASED_PLAN.md` |

---

## 13. Log de progresso (append-only)

| Data | Autor | Mudança |
|------|-------|---------|
| 2026-07-16 | planejamento | Criação do documento; baseline de status a partir do código atual |
| 2026-07-16 | implementação | Fase 0/1: `BinarySettlementGate` + flags V_* + audit `KFE_SETTLEMENT_GATE` + integração no submit; defaults D1/D2/D4/D6/D7 documentados |
| 2026-07-16 | implementação | Fases 3–5 + 7: LND pay status matrix + invoice gateway; payment request LIGHTNING + capabilities; liquidez/circuit breaker no gate; full-blocks-from-now (`BITCOIN_PRUNE_MB=0`); V41 migration; suite `:kfe-service:test` green |
| 2026-07-16 | implementação | Residual 5/4: liquidity reservation HELD→CONSUMED/RELEASED (V42 + advisory lock); free capacity = local − held; wire submit/settle/fail; `KfePaymentRequestLightningMonitor` inbound settle |
| 2026-07-16 | implementação | V_NO_JAMMING (HTLC max + peer denylist); concurrency tests 100 reserves; bitcoin disk alerts Prometheus |
| 2026-07-16 | implementação | Fase 6: channel decision AND gates, LND open/close/ppm, admin `/api/admin/kfe/channels`, V43 decisions table |
| 2026-07-16 | implementação | Drain monitor + rebalance queue V44 + RUNBOOK_KFE_LIGHTNING_E2E_TESTNET.md |
| 2026-07-16 | implementação | Rebalance worker LND circular self-pay + profit fee; admin process endpoint; e2e smoke script |
| 2026-07-16 | implementação | Dead-man capacity: signal store (V_LIQUIDEZ rejects) → capacity jobs V45 → async OPEN/CLOSE via AND gates + SYSTEM_PROFIT; admin `/capacity/*` |
| 2026-07-16 | implementação | Loop fallback client; rebalance+gate metrics; prod-hardening props; kfe-lightning-alerts |
| 2026-07-16 | deploy | Hot-jar deploy to kind; fixed LightningChannelGateway bean; V41–V44 tables present; health UP |

---

## 14. Próxima ação imediata (para não perder contexto)

1. ~~Código do plano de liquidação/LN/canais~~ — **essencialmente completo 2026-07-16**.
2. **Ops:** executar runbook/smoke em testnet real.
3. **Prod cutover:** importar `kfe-service-prod-hardening.properties` + LND + loopd opcional + node_exporter.
4. Monitorar alertas `kfe-lightning-alerts.yml`.

### Admin channels API

- `GET /api/admin/kfe/channels`
- `POST .../open|/open/evaluate`
- `POST .../rebalance|/rebalance/evaluate`
- `POST .../close|/close/evaluate`
- `POST .../ppm|/ppm/evaluate`

### Node storage

Full-blocks-from-now (`BITCOIN_PRUNE_MB=0`). Alertas: `bitcoin-node-storage-alerts.yml`.

Referências: `KfeChannelDecisionService`, `KfeChannelLifecycleService`, `LightningChannelGateway`, V43.

---

*Fim do plano vivo. Qualquer implementação nova de liquidação/Lightning/onchain deve referenciar um ID deste documento (ex: `V-LIQUIDEZ`, `LN-IN-01`, `NODE-01`) no PR.*
