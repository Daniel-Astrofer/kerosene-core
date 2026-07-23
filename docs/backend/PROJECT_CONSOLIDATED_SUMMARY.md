# Project Status / Roadmap (Jul 2026)

## Exec

Solid base; moving prototype → beta. Focus: design governance, finance contracts, **vault-mesh treasury**, kill mocks.

UI maturity note: 7.2/10 (strong brand; token drift + privacy gaps).

## Custody / signing (current)

- **Bank:** `kfe-service` / `com.kerosene.kfe` — Intent/Receipt, no FROST shares
- **Treasury:** vault mesh (`kerosene-vault`) — FROST, Taproot PSBT, day-advance + reshare, governance rewards
- **Deploy:** local-full mesh on, mpc signing off; HashiCorp Raft not treasury SoT
- **Nodes:** domestic PCs first-class; SEV preferred seating; TPM ≠ SEV — see `VAULT_MESH_PLAN.md` §3.1
- **Ceremony:** over-wire FROST DKG (same path prod vs lab; config differs)

## Canonical docs (`docs/`)

- `docs/backend/api/README.md` — domain API
- `docs/backend/API_REFERENCE.md` — inventory
- `docs/backend/INFRASTRUCTURE.md` — Compose/K8s/mesh/Spring
- `docs/backend/BUSINESS_LOGIC.md` — finance rules
- `docs/backend/KFE_ONLY_FINANCIAL_ARCHITECTURE.md` — KFE ownership
- `docs/backend/KFE_SEPARATION_PHASED_PLAN.md` — Core/KFE split
- `docs/backend/TROUBLESHOOTING.md` — ops triage
- `docs/frontend/APP.md` — Flutter/Tor
- Repo root: `VAULT_MESH_PLAN.md` — mesh architecture baseline

## FE / UI

**Ok:** dark-first brand; auth (passkey/TOTP); balance mask + haptics.

**Gaps:**
- local color/radius tokens vs design system
- PT hardcoded outside ARB
- payments UX (quote without recipient check; error timeline)
- privacy: no global screenshot block, app-switcher blur, clipboard wipe

## Mock removal

**Done:** remote Bitcoin accounts; receive-request list; no fake ledger success; no fixed FX; real fee sizing.

**Todo:** admin screens handle `FutureProvider` errors.

## Roadmap

**P0:** mesh Production Gate (mTLS, honest attestation-by-tier, anti-nonce); unify `X-Device-Hash` FE/BE where still drifting.

**P1:** Redis `Idempotency-Key` rule; contract tests ledger/payments; privacy mode for seeds/xpubs.

**P2:** payments flow Recipient→Capabilities→Quote→Auth; token consolidation; CI ban hardcoded strings.

**P3:** DB restore + Tor key rotation runbooks; prod required-env checklist.

**Planned / gaps (not shipped):** full SNP VCEK; CHANNELS→LND inject; deposit xpub vs mesh `tb1p`. Tor ceremony path exists (mTLS); prod deploy still often clearnet lab.

Updated: 2026-07-23.
