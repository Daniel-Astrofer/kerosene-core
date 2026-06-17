# Public, Health e Web API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `17`.

Controllers cobertos:

- `HealthController`
- `PublicSiteController`
- `RootStatusController`
- `Spring Boot Actuator`
- `SystemReleaseController`
- `WebAdminController`
- `WebSocketConfig/STOMP`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/` | `RootStatusController.root` | PUBLIC | none | `Map<String, Object>` | [RootStatusController.java](../../../backend/kerosene/src/main/java/source/common/controller/RootStatusController.java#L22) |
| `GET` | `/` | `WebAdminController.index` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L11) |
| `GET` | `/actuator/health` | `Spring Boot Actuator.health` | PUBLIC<br>cond: `Actuator endpoint, not a domain controller.` | none | `Actuator health payload` | [Security.java](../../../backend/kerosene/src/main/java/source/auth/application/infra/security/Security.java#L55) |
| `GET` | `/actuator/health/**` | `Spring Boot Actuator.healthGroup` | PUBLIC<br>cond: `Actuator endpoint, not a domain controller.` | none | `Actuator health payload` | [Security.java](../../../backend/kerosene/src/main/java/source/auth/application/infra/security/Security.java#L55) |
| `GET` | `/admin` | `WebAdminController.webRoutes` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L17) |
| `GET` | `/admin/**` | `WebAdminController.webRoutes` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L17) |
| `GET` | `/api/public/mobile-download` | `PublicSiteController.mobileDownload` | PUBLIC | none | `MobileDownloadService.MobileReleaseInfo` | [PublicSiteController.java](../../../backend/kerosene/src/main/java/source/common/admin/PublicSiteController.java#L18) |
| `GET` | `/bitcoin-banking` | `WebAdminController.webRoutes` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L17) |
| `GET` | `/bitcoin-banking/**` | `WebAdminController.webRoutes` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L17) |
| `GET` | `/download` | `WebAdminController.webRoutes` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L17) |
| `GET` | `/health/dependencies` | `HealthController.dependencies` | AUTHENTICATED | none | `ResponseEntity<OperationalHealthSnapshot>` | [HealthController.java](../../../backend/kerosene/src/main/java/source/common/controller/HealthController.java#L30) |
| `GET` | `/health/live` | `HealthController.live` | PUBLIC | none | `ResponseEntity<OperationalHealthSnapshot>` | [HealthController.java](../../../backend/kerosene/src/main/java/source/common/controller/HealthController.java#L20) |
| `GET` | `/health/ready` | `HealthController.ready` | PUBLIC | none | `ResponseEntity<OperationalHealthSnapshot>` | [HealthController.java](../../../backend/kerosene/src/main/java/source/common/controller/HealthController.java#L25) |
| `GET` | `/healthz` | `RootStatusController.healthz` | PUBLIC | none | `Map<String, Object>` | [RootStatusController.java](../../../backend/kerosene/src/main/java/source/common/controller/RootStatusController.java#L27) |
| `GET` | `/status` | `WebAdminController.webRoutes` | PUBLIC | none | `String` | [WebAdminController.java](../../../backend/kerosene/src/main/java/source/common/controller/WebAdminController.java#L17) |
| `GET` | `/system/release` | `SystemReleaseController.release` | PUBLIC | none | `ReleaseManifestService.ReleaseSnapshot` | [SystemReleaseController.java](../../../backend/kerosene/src/main/java/source/common/admin/SystemReleaseController.java#L17) |
| `WS` | `/ws/**` | `WebSocketConfig/STOMP.stompEndpoint` | PUBLIC_HANDSHAKE<br>cond: `WebSocket/STOMP surface; message-level auth is configured outside REST controllers.` | none | `STOMP messages` | [WebSocketConfig.java](../../../backend/kerosene/src/main/java/source/config/WebSocketConfig.java#L1) |

## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
