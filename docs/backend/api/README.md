# Backend API Docs

Per-domain ops docs for FE/mobile/QA. Auth, headers, body, responses, removed/replaced routes.

Inventory: `docs/backend/API_REFERENCE.md` (does not replace domain docs).

Source: controllers, DTOs, `EndpointPolicyRegistry`, security under `backend/kerosene/` (`com.kerosene.*`; KFE domain `com.kerosene.kfe`).

## KFE-only

Active finance:

```text
/kfe/**
/api/admin/kfe/**
```

Old finance routes only as `STALE` / `CONTROLLER_ABSENT` / `REMOVED` / migrate notes — not live client contracts.

## Files

| Service | File | State |
| --- | --- | --- |
| Admin ops | — | Outside public repo |
| Audit | [AUDIT.md](AUDIT.md) | `4` live `/api/admin/kfe/audit/**`; old `/audit/**`, `/v1/audit/**` stale |
| Auth | [AUTH.md](AUTH.md) | login, TOTP, passkey, PIN, device-key, recovery, admin |
| Bitcoin accounts | [BITCOIN_ACCOUNTS.md](BITCOIN_ACCOUNTS.md) | no controller; → KFE wallet/UTXO/PSBT |
| Integrations | [INTEGRATIONS.md](INTEGRATIONS.md) | BTCPay policy; no controller; stale |
| KFE | [KFE.md](KFE.md) | wallet, dashboard, receive, tx, quote, PSBT, audit |
| Ledger | [LEDGER.md](LEDGER.md) | no controller; → KFE |
| Mining | [MINING.md](MINING.md) | live |
| Notifications | [NOTIFICATIONS.md](NOTIFICATIONS.md) | live |
| Payments | [PAYMENTS.md](PAYMENTS.md) | via KFE receive+tx; legacy removed |
| Public/health/web | [PUBLIC_HEALTH_WEB.md](PUBLIC_HEALTH_WEB.md) | public, health, web, actuator |
| Sovereignty | [SOVEREIGNTY.md](SOVEREIGNTY.md) | `7` live; HMAC + admin token |
| Transactions/economy | [TRANSACTIONS.md](TRANSACTIONS.md) | `2` Economy + KFE tx refs |
| Treasury / vault mesh | [PUBLIC_HEALTH_WEB.md](PUBLIC_HEALTH_WEB.md), [INFRASTRUCTURE.md](../INFRASTRUCTURE.md) | no legacy treasury controller; custody = vault mesh (`/api/admin/operations/vault-mesh`, mesh `/v1/health`) |
| Wallet | [WALLET.md](WALLET.md) | `/wallet/**` gone; use KFE |
| DTO index | [DTO_SCHEMA_INDEX.md](DTO_SCHEMA_INDEX.md) | aux only |

## Read rules

`STALE` / `CONTROLLER_ABSENT` / `DENIED_BY_DEFAULT` → do not call from clients until controller+service+policy exist.

Prefer:

```text
/auth/**
/kfe/**
/api/economy/**
/api/admin/kfe/audit/**
/mining/**
/notifications/**
/health/**
/sovereignty/**
/quorum/**
```

## Global

- `Security`: CORS, CSRF off, defensive headers, stateless session
- `EndpointPolicyRegistry`: `PUBLIC` / `ADMIN` / `AUTHENTICATED`
- Fallback `anyRequest().denyAll()`
- No policy → may never hit controller
- Filters before REST: `ParanoidSecurityFilter`, `RateLimitFilter`, `JwtAuthenticationFilter`
- `ReleaseAttestationFilter` may require attestation headers when enabled

## Notes

- `DTO_SCHEMA_INDEX.md` does not replace endpoint docs
- `Map<String,Object>` responses may be inferred
- Restoring legacy needs controller + policy + docs update
- Admin treasury health is vault-mesh only (HashiCorp Raft admin routes removed; mpc-sidecar not primary signer)
- Prefer `/api/admin/operations/vault-mesh` + mesh `GET /v1/health` over any Vault Raft readiness probe
