# Soberania e Quorum API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `7`.

Controllers cobertos:

- `QuorumShardController`
- `SovereigntyStatusController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `POST` | `/quorum/commit` | `QuorumShardController.commit` | AUTHENTICATED | body: QuorumRequest | `ResponseEntity<Map<String, Object>>` | [QuorumShardController.java](../../../backend/kerosene/src/main/java/source/sovereign/quorum/QuorumShardController.java#L62) |
| `POST` | `/quorum/health` | `QuorumShardController.health` | AUTHENTICATED | body: QuorumRequest | `ResponseEntity<Map<String, Object>>` | [QuorumShardController.java](../../../backend/kerosene/src/main/java/source/sovereign/quorum/QuorumShardController.java#L32) |
| `POST` | `/quorum/prepare` | `QuorumShardController.prepare` | AUTHENTICATED | body: QuorumRequest | `ResponseEntity<Map<String, Object>>` | [QuorumShardController.java](../../../backend/kerosene/src/main/java/source/sovereign/quorum/QuorumShardController.java#L47) |
| `GET` | `/sovereignty/ping` | `SovereigntyStatusController.ping` | PUBLIC | none | `String` | [SovereigntyStatusController.java](../../../backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java#L199) |
| `POST` | `/sovereignty/reattest` | `SovereigntyStatusController.reAttestNode` | AUTHENTICATED | none | `ResponseEntity<Map<String, String>>` | [SovereigntyStatusController.java](../../../backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java#L141) |
| `GET` | `/sovereignty/status` | `SovereigntyStatusController.getSovereigntyStatus` | PUBLIC | none | `Map<String, Object>` | [SovereigntyStatusController.java](../../../backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java#L56) |
| `GET` | `/sovereignty/telemetry` | `SovereigntyStatusController.getTelemetry` | AUTHENTICATED | none | `ResponseEntity<Map<String, Object>>` | [SovereigntyStatusController.java](../../../backend/kerosene/src/main/java/source/security/SovereigntyStatusController.java#L172) |

## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
