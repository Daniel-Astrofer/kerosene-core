# Notifications API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `5`.

Controllers cobertos:

- `NotificationController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/notifications` | `NotificationController.getNotifications` | AUTHENTICATED | none | `ResponseEntity<List<NotificationEntity>>` | [NotificationController.java](../../../backend/kerosene/src/main/java/source/notification/controller/NotificationController.java#L35) |
| `GET` | `/notifications/device-tokens` | `NotificationController.activeDeviceTokens` | AUTHENTICATED | none | `ResponseEntity<List<DeviceTokenResponse>>` | [NotificationController.java](../../../backend/kerosene/src/main/java/source/notification/controller/NotificationController.java#L53) |
| `DELETE` | `/notifications/device-tokens/{id}` | `NotificationController.revokeToken` | AUTHENTICATED | path: id: Long | `ResponseEntity<Void>` | [NotificationController.java](../../../backend/kerosene/src/main/java/source/notification/controller/NotificationController.java#L60) |
| `POST` | `/notifications/register-token` | `NotificationController.registerToken` | AUTHENTICATED | body: DeviceTokenRegisterRequest | `ResponseEntity<DeviceTokenResponse>` | [NotificationController.java](../../../backend/kerosene/src/main/java/source/notification/controller/NotificationController.java#L46) |
| `PUT` | `/notifications/{id}/read` | `NotificationController.markAsRead` | AUTHENTICATED | path: id: Long | `ResponseEntity<Void>` | [NotificationController.java](../../../backend/kerosene/src/main/java/source/notification/controller/NotificationController.java#L40) |

## DTOs e Payloads

### `DeviceTokenRegisterRequest`

Fonte: [DeviceTokenRegisterRequest.java](../../../backend/kerosene/src/main/java/source/notification/dto/DeviceTokenRegisterRequest.java)

Campos observados no DTO:

- `String platform`
- `String token`
- `String deviceId`
- `String appVersion`

### `DeviceTokenResponse`

Fonte: [DeviceTokenResponse.java](../../../backend/kerosene/src/main/java/source/notification/dto/DeviceTokenResponse.java)

Campos observados no DTO:

- `Long id`
- `String platform`
- `String tokenRef`
- `String deviceRef`
- `String appVersion`
- `LocalDateTime createdAt`
- `LocalDateTime lastSeenAt`
- `LocalDateTime revokedAt`
- `boolean active`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
