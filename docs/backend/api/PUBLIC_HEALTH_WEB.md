# Public / Health / Web API

Health, root status, web/admin SPA, actuator surface.

## Globals
- Envelope `ApiResponse`: `success`,`message`,`data`,`errorCode`,`timestamp`
- Authed: `Authorization: Bearer <jwt>`; body: `Content-Type: application/json`

## Endpoints

### Liveness health
- `GET /health/live`
- auth `PUBLIC`
- does: HTTP process alive.
- rules: O controller retorna 200 quando snapshot não está DOWN e 503 quando está DOWN.
- ok: 200 OK
- resp: `status`, `generatedAt`, `components`, `details`
- status: `200`/`200`/`503`

### Readiness health
- `GET /health/ready`
- auth `PUBLIC`
- does: Ready for traffic.
- rules: O controller retorna 200 quando snapshot não está DOWN e 503 quando está DOWN.
- ok: 200 OK
- resp: `status`, `generatedAt`, `components`, `details`
- status: `200`/`200`/`503`

### Dependencies health
- `GET /health/dependencies`
- auth `AUTHENTICATED`
- does: Dependency health.
- rules: O controller retorna 200 quando snapshot não está DOWN e 503 quando está DOWN.
- headers: `Authorization`
- ok: 200 OK
- resp: `status`, `generatedAt`, `components`, `details`
- status: `200`/`200`/`503`

### Root status
- `GET /`
- auth `PUBLIC`
- does: Root JSON status.
- rules: Endpoint público raiz.
- ok: 200 OK
- resp: `application`, `status`
- status: `200`

### Healthz
- `GET /healthz`
- auth `PUBLIC`
- does: Basic /healthz.
- rules: Payload é raw map.
- ok: 200 OK
- resp: `status`
- status: `200`

## Vault mesh (treasury health)

Custody plane is **vault mesh**, not HashiCorp Vault Raft readiness.

- Mesh node: `GET /v1/health` on `kerosene-vault` (exposes `node_tier`, `attestation_mode`, `tee_available`; TPM ≠ SEV).
- Admin: `GET /api/admin/operations/vault-mesh` (JWT `ROLE_ADMIN`) — snapshot via `VaultMeshHealthService`.
- Local-full: mesh on, mpc signing off. See [INFRASTRUCTURE.md](../INFRASTRUCTURE.md).
