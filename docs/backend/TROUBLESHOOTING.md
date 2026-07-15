# Kerosene Backend Troubleshooting

Status: developer guide for Kerosene backend debugging  
Scope: fast diagnostics, logs, audit events, KFE-only checks, focused validation, and orchestration helpers

## First Rule

Do not start every container just to see what happens.

For this orchestration, `infra/scripts/local/control.sh start` is intentionally disabled until the user explicitly re-enables it. Prefer fast read-only checks, focused logs, startup diagnostics, and targeted tests before touching long-running local services.

## Fast Decision Flow

1. Check the worktree.

```bash
git status --short
```

If the tree is dirty, inspect only the relevant diff and avoid mixing unrelated work into the troubleshooting change.

2. Identify the failing surface.

- Startup/config: profiles, required env vars, Flyway safety, datasource, Redis, Vault, MPC, LND, or KFE-only assumptions.
- Runtime request failure: trace the request by `traceId` or `correlationId`.
- KFE financial behavior: verify `/kfe/**` or `/api/admin/kfe/**` only, then run focused KFE tests.
- Frontend/backend mismatch: run focused Flutter analysis/tests around the affected client code.
- Orchestration issue: use the MCP helper status/result tools before dispatching more work.

3. Use the narrowest diagnostic.

- If the process crashes before serving traffic, inspect startup diagnostics and startup logs first.
- If a request fails, search logs by `traceId`, `correlationId`, `event`, `domain`, and `operation`.
- If money state changed, inspect audit events and transaction/outbox state before retrying the action.
- If a legacy route appears, run the KFE-only verifier before editing API clients or docs.

4. Validate the smallest useful scope.

```bash
git diff --check
```

Then add one focused Gradle or Flutter command only for the touched area.

## Crash Triage Without Blind Container Restarts

Use this order when a backend container exits or restarts:

1. Confirm whether the crash is startup or runtime.

```bash
docker compose ps
docker compose logs --tail=200 <backend-service>
```

2. Read startup signals before restarting anything.

Look for structured fields and markers from the startup/runtime logging contract:

- `domain=startup`
- `event`
- `operation`
- `exceptionType`
- `correlationId`
- `traceId` when available

3. Classify the failure.

| Signal | Likely area | Next check |
| --- | --- | --- |
| Profile or production safety failure | configuration | active profiles and fail-closed production checks |
| Flyway error | database migration safety | migration order and datasource config |
| datasource connection failure | database | DB service status and credentials source, without printing secrets |
| Redis unavailable | cache/session/rate limit | Redis service status and profile expectation |
| Vault readiness or attestation failure | security/Vault | Vault readiness, seal/quorum, attestation config |
| MPC signer unavailable | signing | MPC sidecar availability and signer mode |
| LND or rail provider failure | KFE rail | provider config and retry/outbox state |
| legacy financial route/package hit | KFE-only regression | `scripts/verify-kfe-only.sh` |

4. Restart only the failing dependency when the evidence points to it. Do not run `infra/scripts/local/control.sh start` in this orchestration.

## Trace And Correlation IDs

Every request should carry or receive an `X-Correlation-Id`. `LogContextFilter` accepts safe `X-Correlation-Id` or `X-Request-Id` values and returns the selected correlation id in the response header.

For request failures:

```bash
rg 'correlationId=<id>|"correlationId":"<id>"' logs backend -g '*.log'
rg 'traceId=<id>|"traceId":"<id>"' logs backend -g '*.log'
```

Prefer a stable `correlationId` supplied by the client during reproduction. Use `traceId` to join backend logs emitted by tracing, exception handling, and downstream operations.

## Structured Logs

Runtime logs should be searched by structured fields, not only free text.

Expected log domains:

- `runtime`
- `startup`
- `security`
- `auth`
- `kfe`
- `audit`
- `integration`
- `vault`
- `mpc`
- `frontend-api`
- `access`

Useful fields:

- `event`
- `domain`
- `operation`
- `message`
- `exceptionType`
- `correlationId`
- `traceId`
- sanitized identifiers such as wallet, transaction, audit, or user references

Never add raw secrets, tokens, private keys, macaroons, credentials, invoices, request bodies, or provider payloads to logs.

## Audit Events

Audit events are for incident reconstruction, not developer narration. Use them when the question is, "What business/security event actually happened?"

Important event families:

- Auth/session: `AUTH_LOGIN_SUCCEEDED`, `AUTH_LOGIN_FAILED`, `AUTH_LOGOUT`, `JWT_SESSION_REVOKED`
- Admin access: `ADMIN_ACCESS_REQUESTED`, `ADMIN_ACCESS_APPROVED`, `ADMIN_ACCESS_REJECTED`, `ADMIN_ACCESS_REDEEMED`
- KFE: `KFE_WALLET_CREATED`, `KFE_TRANSACTION_SUBMITTED`, `KFE_IDEMPOTENCY_CONFLICT`, `KFE_OUTBOX_DISPATCHED`, `KFE_OUTBOX_RETRY`, `KFE_SETTLEMENT_COMPLETED`, `KFE_SETTLEMENT_FAILED`, `KFE_INBOUND_CREDITED`, `KFE_INBOUND_DUPLICATE_REJECTED`
- Vault/MPC: `VAULT_ATTESTATION_SUCCEEDED`, `VAULT_ATTESTATION_FAILED`, `MPC_SIGN_REJECTED`, `MPC_UNSUPPORTED_MODE_REJECTED`

When debugging KFE money state:

1. Find the transaction or wallet reference in structured logs.
2. Find matching audit events by event type and sanitized identifiers.
3. Check for idempotency conflicts before retrying.
4. Check outbox dispatch/retry/final status before calling providers again.

## KFE-Only Verification

Financial code and docs must use active KFE paths only:

```text
/kfe/**
/api/admin/kfe/**
```

Run the verifier when a financial route, client, test, or doc changes:

```bash
scripts/verify-kfe-only.sh
```

For docs-only audits of legacy financial route mentions:

```bash
STRICT_DOCS=1 scripts/verify-kfe-only.sh
```

If the verifier fails, fix the KFE-only violation first. Do not add compatibility aliases for legacy ledger, wallet, payment, treasury, bitcoin-account, deposit, or transaction routes unless a separate task explicitly reintroduces them.

## Focused Tests

Backend:

```bash
cd backend/kerosene
./gradlew test --tests 'source.package.ClassNameTest'
./gradlew test --tests '*Kfe*'
```

Use focused test patterns around the touched use case, controller, audit logger, startup diagnostic, or KFE invariant. Escalate to broader Gradle tasks only after the focused failure is understood.

Frontend:

```bash
cd frontend
flutter analyze
flutter test test/path/to/focused_test.dart
```

Use Flutter checks when the failure involves API client DTOs, trace/correlation display, or KFE-only frontend routes.

## MCP Orchestration Helpers

Use the read-only MCP helpers to inspect orchestration state before launching or committing agent work:

- `kerosene_cycle_once`: run one controlled nightly orchestration cycle. It accepts `{"mode":"nightly"}`.
- `kerosene_git_status`: compact repository or agent worktree status.
- `kerosene_clean_worktree`: dirty tree inspection/cleanup flow without discarding unknown work.
- `kerosene_dispatch_next`: dispatch the next nightly queue item.
- `kerosene_collect_agent_result`: status, tail, and dirty-worktree info for a managed agent.
- `kerosene_commit_agent_output`: validate, stage enumerated files, commit, and cherry-pick finished agent output.

Do not dispatch another implementation agent while one is active. Keep task commits isolated and use the queue commit format, for example:

```text
fase-6/docs: update developer troubleshooting guide
```

## Minimal Closeout

For any troubleshooting change, record:

- what failed;
- the `traceId` or `correlationId`, if available;
- the structured log `event`, `domain`, and `operation`;
- relevant audit event type, if money/security state changed;
- the focused validation command and result;
- whether broader validation was skipped and why.
