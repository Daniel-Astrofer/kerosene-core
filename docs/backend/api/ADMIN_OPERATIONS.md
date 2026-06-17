# Admin Operations API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `9`.

Controllers cobertos:

- `AdminOperationsController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/api/admin/operations/blockchain` | `AdminOperationsController.blockchain` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `Map<String, Object>` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L92) |
| `GET` | `/api/admin/operations/health` | `AdminOperationsController.health` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `OperationalHealthSnapshot` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L87) |
| `GET` | `/api/admin/operations/lightning` | `AdminOperationsController.lightning` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `Map<String, Object>` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L140) |
| `GET` | `/api/admin/operations/logs` | `AdminOperationsController.logs` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | query: limit: int | `List<Map<String, Object>>` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L189) |
| `GET` | `/api/admin/operations/metrics` | `AdminOperationsController.metrics` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `Map<String, Object>` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L198) |
| `GET` | `/api/admin/operations/mobile` | `AdminOperationsController.mobile` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `MobileDownloadService.MobileReleaseInfo` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L184) |
| `GET` | `/api/admin/operations/overview` | `AdminOperationsController.overview` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `Map<String, Object>` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L74) |
| `GET` | `/api/admin/operations/release` | `AdminOperationsController.release` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ReleaseManifestService.ReleaseSnapshot` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L179) |
| `GET` | `/api/admin/operations/vault-raft` | `AdminOperationsController.vaultRaft` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `VaultRaftHealthService.VaultRaftSnapshot` | [AdminOperationsController.java](../../../backend/kerosene/src/main/java/source/common/admin/AdminOperationsController.java#L174) |

## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
