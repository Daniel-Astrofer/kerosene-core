# Auth e Conta API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `48`.

Controllers cobertos:

- `AccountActivationController`
- `AccountSecurityController`
- `AccountSecurityStatusController`
- `AdminAccessController`
- `AppPinController`
- `BackupCodesController`
- `DeviceKeyController`
- `EmergencyRecoveryController`
- `MeController`
- `PasskeyController`
- `TotpController`
- `UserController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/auth/activation-status` | `AccountActivationController.getStatus` | AUTHENTICATED | none | `ApiResponse<AccountActivationStatusDTO>` | [AccountActivationController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AccountActivationController.java#L28) |
| `POST` | `/auth/activation-status/funding-link` | `AccountActivationController.createFundingLink` | AUTHENTICATED | none | `ApiResponse<AccountActivationStatusDTO>` | [AccountActivationController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AccountActivationController.java#L34) |
| `POST` | `/auth/activation-status/{linkId}/confirm` | `AccountActivationController.confirm` | AUTHENTICATED | path: linkId: String<br>body: Map<String, String> | `ApiResponse<AccountActivationStatusDTO>` | [AccountActivationController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AccountActivationController.java#L43) |
| `GET` | `/auth/admin/access-attempts/pending` | `AdminAccessController.pendingAttempts` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ApiResponse<List<AdminAccessAttemptDTO>>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L95) |
| `POST` | `/auth/admin/access-attempts/{attemptId}/decision` | `AdminAccessController.decide` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | path: attemptId: UUID<br>body: AdminAccessDecisionRequestDTO | `ApiResponse<AdminAccessAttemptDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L103) |
| `GET` | `/auth/admin/devices` | `AdminAccessController.devices` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ApiResponse<List<AdminAuthenticatedDeviceDTO>>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L115) |
| `POST` | `/auth/admin/devices/{deviceId}/block` | `AdminAccessController.blockDevice` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | path: deviceId: String | `ApiResponse<AdminAuthenticatedDeviceDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L123) |
| `POST` | `/auth/admin/devices/{deviceId}/revoke` | `AdminAccessController.revokeDevice` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | path: deviceId: String | `ApiResponse<AdminAuthenticatedDeviceDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L134) |
| `DELETE` | `/auth/admin/key` | `AdminAccessController.revokeKey` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ApiResponse<AdminKeyStatusDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L87) |
| `GET` | `/auth/admin/key` | `AdminAccessController.keyStatus` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | none | `ApiResponse<AdminKeyStatusDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L79) |
| `POST` | `/auth/admin/key` | `AdminAccessController.createOrRotateKey` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")` | body: AdminKeyCreateRequestDTO | `ApiResponse<AdminKeyStatusDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L71) |
| `POST` | `/auth/admin/login` | `AdminAccessController.startLogin` | PUBLIC | body: AdminLoginRequestDTO | `ApiResponse<AdminLoginResponseDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L41) |
| `GET` | `/auth/admin/login/{attemptId}` | `AdminAccessController.pollLogin` | PUBLIC | path: attemptId: UUID | `ApiResponse<AdminLoginResponseDTO>` | [AdminAccessController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AdminAccessController.java#L58) |
| `GET` | `/auth/backup-codes` | `BackupCodesController.getStatus` | AUTHENTICATED | none | `ApiResponse<BackupCodesStatusDTO>` | [BackupCodesController.java](../../../backend/kerosene/src/main/java/source/auth/controller/BackupCodesController.java#L24) |
| `POST` | `/auth/backup-codes/regenerate` | `BackupCodesController.regenerate` | AUTHENTICATED | none | `ApiResponse<BackupCodesStatusDTO>` | [BackupCodesController.java](../../../backend/kerosene/src/main/java/source/auth/controller/BackupCodesController.java#L30) |
| `GET` | `/auth/device-key/challenge` | `DeviceKeyController.getChallenge` | PUBLIC | query: username: String | `ApiResponse<DeviceKeyChallengeResponse>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L140) |
| `GET` | `/auth/device-key/devices` | `DeviceKeyController.getRegisteredDevices` | AUTHENTICATED | none | `ApiResponse<List<DeviceKeyDeviceDTO>>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L268) |
| `POST` | `/auth/device-key/devices/{credentialId}/revoke` | `DeviceKeyController.revokeDevice` | AUTHENTICATED | path: credentialId: String | `ApiResponse<List<DeviceKeyDeviceDTO>>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L281) |
| `POST` | `/auth/device-key/onboarding/finish` | `DeviceKeyController.finishOnboardingRegistration` | PUBLIC | query: sessionId: String<br>body: DeviceKeyRegistrationRequest | `ApiResponse<String>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L99) |
| `POST` | `/auth/device-key/onboarding/start` | `DeviceKeyController.startOnboardingRegistration` | PUBLIC | query: sessionId: String, username: String | `ApiResponse<DeviceKeyChallengeResponse>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L76) |
| `POST` | `/auth/device-key/register/finish` | `DeviceKeyController.finishAuthenticatedRegistration` | AUTHENTICATED | body: DeviceKeyRegistrationRequest | `ApiResponse<String>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L165) |
| `POST` | `/auth/device-key/register/start` | `DeviceKeyController.startAuthenticatedRegistration` | AUTHENTICATED | none | `ApiResponse<DeviceKeyChallengeResponse>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L152) |
| `POST` | `/auth/device-key/verify` | `DeviceKeyController.verifyAndLogin` | PUBLIC | body: DeviceKeyVerifyRequest | `ApiResponse<Object>` | [DeviceKeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/DeviceKeyController.java#L188) |
| `POST` | `/auth/login` | `UserController.login` | PUBLIC | body: UserDTO | `ApiResponse<String>` | [UserController.java](../../../backend/kerosene/src/main/java/source/auth/controller/UserController.java#L43) |
| `POST` | `/auth/login/totp/verify` | `UserController.verifyLoginTotpCode` | PUBLIC | body: UserDTO | `ApiResponse<String>` | [UserController.java](../../../backend/kerosene/src/main/java/source/auth/controller/UserController.java#L69) |
| `GET` | `/auth/me` | `MeController.getCurrentUser` | AUTHENTICATED | header: X-Device-Hash (optional) | `ApiResponse<Map<String, Object>>` | [MeController.java](../../../backend/kerosene/src/main/java/source/auth/controller/MeController.java#L33) |
| `GET` | `/auth/passkey/challenge` | `PasskeyController.getChallenge` | PUBLIC | query: username: String | `ApiResponse<String>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L70) |
| `GET` | `/auth/passkey/devices` | `PasskeyController.getRegisteredDevices` | AUTHENTICATED | none | `ApiResponse<PasskeyInventoryDTO>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L76) |
| `POST` | `/auth/passkey/devices/{deviceInstallId}/block` | `PasskeyController.blockDevice` | AUTHENTICATED | path: deviceInstallId: String | `ApiResponse<PasskeyInventoryDTO>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L105) |
| `POST` | `/auth/passkey/devices/{deviceInstallId}/revoke` | `PasskeyController.revokeDevice` | AUTHENTICATED | path: deviceInstallId: String | `ApiResponse<PasskeyInventoryDTO>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L110) |
| `POST` | `/auth/passkey/onboarding/finish` | `PasskeyController.finishOnboardingRegistration` | PUBLIC | query: sessionId: String<br>body: PasskeyRegistrationRequest | `ApiResponse<String>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L132) |
| `POST` | `/auth/passkey/onboarding/start` | `PasskeyController.startOnboardingRegistration` | PUBLIC | query: sessionId: String | `ApiResponse<String>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L120) |
| `POST` | `/auth/passkey/register` | `PasskeyController.registerPasskey` | AUTHENTICATED | body: PasskeyRegistrationRequest | `ApiResponse<String>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L95) |
| `POST` | `/auth/passkey/verify` | `PasskeyController.verifyAndLogin` | PUBLIC | body: PasskeyVerifyRequest | `ApiResponse<Object>` | [PasskeyController.java](../../../backend/kerosene/src/main/java/source/auth/controller/PasskeyController.java#L115) |
| `GET` | `/auth/pow/challenge` | `UserController.getPowChallenge` | PUBLIC | none | `ApiResponse<Map<String, String>>` | [UserController.java](../../../backend/kerosene/src/main/java/source/auth/controller/UserController.java#L37) |
| `POST` | `/auth/recovery/emergency/finish` | `EmergencyRecoveryController.finish` | PUBLIC | body: EmergencyRecoveryFinishRequest | `ApiResponse<EmergencyRecoveryFinishResponse>` | [EmergencyRecoveryController.java](../../../backend/kerosene/src/main/java/source/auth/controller/EmergencyRecoveryController.java#L53) |
| `POST` | `/auth/recovery/emergency/start` | `EmergencyRecoveryController.start` | PUBLIC | body: EmergencyRecoveryStartRequest | `ApiResponse<EmergencyRecoveryStartResponse>` | [EmergencyRecoveryController.java](../../../backend/kerosene/src/main/java/source/auth/controller/EmergencyRecoveryController.java#L29) |
| `GET` | `/auth/security-status` | `AccountSecurityStatusController.getStatus` | AUTHENTICATED | none | `ApiResponse<AccountSecurityStatusDTO>` | [AccountSecurityStatusController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AccountSecurityStatusController.java#L23) |
| `GET` | `/auth/security/app-pin` | `AppPinController.getStatus` | AUTHENTICATED | header: X-Device-Hash (optional) | `ApiResponse<AppPinStatusDTO>` | [AppPinController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AppPinController.java#L29) |
| `PUT` | `/auth/security/app-pin` | `AppPinController.configure` | AUTHENTICATED | header: X-Device-Hash (optional)<br>body: ConfigureAppPinRequestDTO | `ApiResponse<AppPinStatusDTO>` | [AppPinController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AppPinController.java#L37) |
| `POST` | `/auth/security/app-pin/verify` | `AppPinController.verify` | AUTHENTICATED | header: X-Device-Hash (optional)<br>body: VerifyAppPinRequestDTO | `ApiResponse<AppPinStatusDTO>` | [AppPinController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AppPinController.java#L46) |
| `GET` | `/auth/security/profile` | `AccountSecurityController.getProfile` | AUTHENTICATED | header: X-Device-Hash (optional) | `ApiResponse<AccountSecurityProfileDTO>` | [AccountSecurityController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AccountSecurityController.java#L47) |
| `PUT` | `/auth/security/profile` | `AccountSecurityController.updateProfile` | AUTHENTICATED | header: X-Device-Hash (optional)<br>body: AccountSecurityUpdateRequestDTO | `ApiResponse<AccountSecurityProfileDTO>` | [AccountSecurityController.java](../../../backend/kerosene/src/main/java/source/auth/controller/AccountSecurityController.java#L62) |
| `POST` | `/auth/signup` | `UserController.signup` | PUBLIC | body: UserDTO | `ApiResponse<SignupResponseDTO>` | [UserController.java](../../../backend/kerosene/src/main/java/source/auth/controller/UserController.java#L50) |
| `POST` | `/auth/signup/totp/verify` | `UserController.verifySignupTotpCode` | PUBLIC | body: SignupTotpVerifyRequestDTO | `ApiResponse<String>` | [UserController.java](../../../backend/kerosene/src/main/java/source/auth/controller/UserController.java#L57) |
| `DELETE` | `/auth/totp` | `TotpController.disable` | AUTHENTICATED | none | `ApiResponse<String>` | [TotpController.java](../../../backend/kerosene/src/main/java/source/auth/controller/TotpController.java#L45) |
| `POST` | `/auth/totp/setup` | `TotpController.setup` | AUTHENTICATED | none | `ApiResponse<TotpSetupResponseDTO>` | [TotpController.java](../../../backend/kerosene/src/main/java/source/auth/controller/TotpController.java#L28) |
| `POST` | `/auth/totp/verify` | `TotpController.verify` | AUTHENTICATED | body: Map<String, String> | `ApiResponse<BackupCodesStatusDTO>` | [TotpController.java](../../../backend/kerosene/src/main/java/source/auth/controller/TotpController.java#L35) |

## Guia detalhado de uso por endpoint

Esta seção complementa a matriz acima. Todos os endpoints que retornam `ApiResponse<T>` usam o envelope `{ success, message, data, errorCode, timestamp }`. Endpoints autenticados exigem `Authorization: Bearer <JWT>`. Endpoints com body exigem `Content-Type: application/json`. `X-Correlation-Id` e `X-Request-Id` são opcionais para rastreabilidade.

### `GET /auth/pow/challenge`

**Para que serve:** gerar um desafio de prova de trabalho para reduzir abuso em cadastro/login público.

| Item | Valor |
|---|---|
| Auth | `PUBLIC` |
| Headers obrigatórios | nenhum |
| Query params | nenhum |
| Body | nenhum |
| Sucesso | `200 OK` com `ApiResponse<Map<String,String>>` |

Response completo esperado:

```json
{
  "success": true,
  "message": "PoW Challenge generated",
  "data": {
    "challenge": "a1b2c3"
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

Status codes: `200` quando gerado; `429` se o rate limit global bloquear; `500` se o gerador de challenge falhar.

### `POST /auth/signup`

**Para que serve:** iniciar cadastro e criar estado temporário de onboarding.

| Campo do body | Tipo | Obrigatório | Nullable | Validação/regra | Descrição |
|---|---:|:---:|:---:|---|---|
| `username` | string | sim | não | `@NotBlank`, tamanho 3..100 | Identificador do usuário. |
| `password` | string/char[] | sim | não | aceita alias `passphrase`; write-only | Senha/passphrase usada pelo service. |
| `totpSecret` | string | não | sim | campo do DTO | Segredo temporário quando usado internamente. |
| `totpCode` | string | condicional | sim | write-only | Código TOTP quando o fluxo solicitar. |
| `voucherCode` | string | não | sim | sem anotação no DTO | Voucher/convite. |
| `challenge` | string | condicional | sim | usado com PoW | Challenge recebido em `/auth/pow/challenge`. |
| `nonce` | string | condicional | sim | usado com PoW | Nonce calculado pelo cliente. |
| `preAuthToken` | string | não | sim | usado em fluxos intermediários | Token temporário quando aplicável. |
| `sessionId` | string | não | sim | usado em continuidade de onboarding | Sessão temporária. |
| `accountSecurity` | enum | não | sim | default `STANDARD` | `STANDARD`, `SHAMIR`, `MULTISIG_2FA`, `PASSKEY`. |
| `shamirTotalShares` | integer | condicional | sim | usado em `SHAMIR` | Total de shares. |
| `shamirThreshold` | integer | condicional | sim | usado em `SHAMIR` | Threshold mínimo. |
| `multisigThreshold` | integer | condicional | sim | usado em `MULTISIG_2FA` | Threshold multisig. |
| `backupCodes` | array string | não | sim | campo do DTO | Códigos de recuperação quando o fluxo usa. |

Exemplo:

```bash
curl -X POST 'http://localhost:8080/auth/signup' \
  -H 'Content-Type: application/json' \
  --data '{"username":"alice","password":"<secret>","challenge":"a1b2c3","nonce":"00001234","accountSecurity":"STANDARD"}'
```

Response completo:

```json
{
  "success": true,
  "message": "Signup initialized. Please verify TOTP to complete registration.",
  "data": {
    "sessionId": "signup-session-id",
    "otpUri": "otpauth://totp/Kerosene:alice?secret=ABC",
    "backupCodes": ["ABCD-EFGH"],
    "totpOptional": false
  },
  "timestamp": "2026-06-19T10:30:00"
}
```

Status codes: `200` cadastro iniciado; `400` username/body inválido; `409` usuário ou sessão conflitante; `429` abuso/rate limit.

### `POST /auth/signup/totp/verify`

**Para que serve:** validar TOTP do cadastro e avançar ou concluir o onboarding.

| Campo | Tipo | Obrigatório | Nullable | Validação | Descrição |
|---|---:|:---:|:---:|---|---|
| `sessionId` | string | sim | não | `@NotBlank` | Sessão retornada pelo signup. |
| `totpCode` | string | sim | não | código TOTP | Código do app autenticador. |

Sucesso: `202 Accepted` com `ApiResponse<String>`. Erros: `400` body inválido; `401` código inválido/sessão inválida; `404` sessão não encontrada; `429` rate limit.

### `POST /auth/login` e `POST /auth/login/totp/verify`

**Para que servem:** iniciar login e concluir login quando TOTP for exigido.

`/auth/login` recebe `UserDTO`; normalmente usa `username` e `password`. `/auth/login/totp/verify` recebe `UserDTO` com `username`, `totpCode` e, conforme service, `preAuthToken` ou `sessionId`.

| Endpoint | Body mínimo | Sucesso | Observação |
|---|---|---|---|
| `POST /auth/login` | `username`, `password` | `202 Accepted` com string/token intermediário ou final | Pode exigir TOTP. |
| `POST /auth/login/totp/verify` | `username`, `totpCode`, token/sessão intermediária | `202 Accepted` com JWT final | Conclui segundo fator. |

Status codes: `202` aceito; `400` payload inválido; `401` credenciais/código inválidos; `404` usuário/sessão ausente; `429` rate limit.

### Conta autenticada e segurança

| Endpoint | Para que serve | Headers | Body | Response |
|---|---|---|---|---|
| `GET /auth/me` | Retorna usuário atual e contexto de sessão. | `Authorization`; `X-Device-Hash` opcional | nenhum | `ApiResponse<Map<String,Object>>` |
| `GET /auth/security-status` | Status resumido: senha, passkey, TOTP, backup codes, ativação. | `Authorization` | nenhum | `ApiResponse<AccountSecurityStatusDTO>` |
| `GET /auth/security/profile` | Perfil completo de segurança. | `Authorization`; `X-Device-Hash` opcional | nenhum | `ApiResponse<AccountSecurityProfileDTO>` |
| `PUT /auth/security/profile` | Atualiza modo de segurança. | `Authorization`; `Content-Type`; `X-Device-Hash` opcional | `AccountSecurityUpdateRequestDTO` | `ApiResponse<AccountSecurityProfileDTO>` |

`AccountSecurityUpdateRequestDTO` aceita `accountSecurity`, `shamirTotalShares`, `shamirThreshold`, `multisigThreshold`. Status codes: `200`, `400`, `401`, `403`, `422` quando a combinação de segurança não for aceita pelo service.

### Ativação de conta

| Endpoint | Para que serve | Path/query | Body | Response |
|---|---|---|---|---|
| `GET /auth/activation-status` | Consultar se a conta pode receber inbound. | nenhum | nenhum | `AccountActivationStatusDTO` |
| `POST /auth/activation-status/funding-link` | Criar link/endereço de funding de ativação. | nenhum | nenhum | `AccountActivationStatusDTO` |
| `POST /auth/activation-status/{linkId}/confirm` | Confirmar funding/link. | `linkId` string | `Map<String,String>`, normalmente comprovante/txid | `AccountActivationStatusDTO` |

Campos retornados: `activated`, `canReceiveInbound`, `requiresActivationDeposit`, `requiredAmountBtc`, `paymentLinkId`, `depositAddress`, `paymentStatus`, `warningMessage`, `activatedAt`.

### TOTP, backup codes e App PIN

| Endpoint | Para que serve | Body | Response | Status relevantes |
|---|---|---|---|---|
| `POST /auth/totp/setup` | Gera `otpUri` e `secret`. | nenhum | `TotpSetupResponseDTO` | `200`, `401` |
| `POST /auth/totp/verify` | Habilita TOTP após validar código. | `Map<String,String>` com `totpCode` | `BackupCodesStatusDTO` | `200`, `400`, `401` |
| `DELETE /auth/totp` | Desabilita TOTP. | nenhum | `ApiResponse<String>` com `OK` | `200`, `401`, `403` |
| `GET /auth/backup-codes` | Consulta status. | nenhum | `BackupCodesStatusDTO` | `200`, `401` |
| `POST /auth/backup-codes/regenerate` | Gera novos códigos. | nenhum | `BackupCodesStatusDTO` com `newlyGeneratedCodes` | `200`, `401`, `403` |
| `GET /auth/security/app-pin` | Consulta status do PIN. | nenhum | `AppPinStatusDTO` | `200`, `401` |
| `PUT /auth/security/app-pin` | Configura PIN. | `enabled`, `pin`, `currentPin`, `totpCode` | `AppPinStatusDTO` | `200`, `400`, `401`, `423` lógico via DTO `locked` |
| `POST /auth/security/app-pin/verify` | Verifica PIN. | `pin` | `AppPinStatusDTO` | `200`, `400`, `401` |

### Passkey

Todos os endpoints abaixo usam WebAuthn/passkey e retornam `ApiResponse`. Bodies de registro usam `PasskeyRegistrationRequest`; bodies de verificação usam `PasskeyVerifyRequest`.

| Endpoint | Auth | Para que serve | Params/body | Response |
|---|---|---|---|---|
| `GET /auth/passkey/challenge?username={username}` | PUBLIC | Gerar challenge de login. | query `username` | `String` challenge |
| `GET /auth/passkey/devices` | AUTHENTICATED | Listar inventário. | nenhum | `PasskeyInventoryDTO` |
| `POST /auth/passkey/register` | AUTHENTICATED | Registrar passkey logado. | `PasskeyRegistrationRequest` | `String` status |
| `POST /auth/passkey/devices/{deviceInstallId}/block` | AUTHENTICATED | Bloquear device. | path `deviceInstallId` | `PasskeyInventoryDTO` |
| `POST /auth/passkey/devices/{deviceInstallId}/revoke` | AUTHENTICATED | Revogar device. | path `deviceInstallId` | `PasskeyInventoryDTO` |
| `POST /auth/passkey/verify` | PUBLIC | Login passkey. | `PasskeyVerifyRequest` | `Object`: JWT ou ação requerida |
| `POST /auth/passkey/onboarding/start?sessionId={sessionId}` | PUBLIC | Challenge passkey no signup. | query `sessionId` | `String` challenge |
| `POST /auth/passkey/onboarding/finish?sessionId={sessionId}` | PUBLIC | Finaliza passkey no signup. | query `sessionId` + `PasskeyRegistrationRequest` | `String` status |

Status codes: `200` sucesso; `400` assertion/body inválido; `401` assinatura/credencial inválida; `404` sessão/device não encontrado; `412` pré-condição criptográfica falhou.

### Device key

| Endpoint | Auth | Para que serve | Params/body | Response |
|---|---|---|---|---|
| `POST /auth/device-key/onboarding/start?sessionId={id}` | PUBLIC | Gera challenge de device-key no onboarding. | query `sessionId`; `username` opcional | `DeviceKeyChallengeResponse` |
| `POST /auth/device-key/onboarding/finish?sessionId={id}` | PUBLIC | Finaliza device-key no onboarding. | query `sessionId` + `DeviceKeyRegistrationRequest` | `String` status |
| `GET /auth/device-key/challenge?username={username}` | PUBLIC | Challenge login device-key. | query `username` | `DeviceKeyChallengeResponse` |
| `POST /auth/device-key/register/start` | AUTHENTICATED | Challenge para registrar logado. | nenhum | `DeviceKeyChallengeResponse` |
| `POST /auth/device-key/register/finish` | AUTHENTICATED | Finaliza registro logado. | `DeviceKeyRegistrationRequest` | `String` status |
| `POST /auth/device-key/verify` | PUBLIC | Login via device-key. | `DeviceKeyVerifyRequest` | `Object`: JWT ou ação requerida |
| `GET /auth/device-key/devices` | AUTHENTICATED | Lista device keys. | nenhum | `List<DeviceKeyDeviceDTO>` |
| `POST /auth/device-key/devices/{credentialId}/revoke` | AUTHENTICATED | Revoga device key. | path `credentialId` | `List<DeviceKeyDeviceDTO>` |

`DeviceKeyChallengeResponse` retorna `challengeId`, `challenge`, `expiresInSeconds`, `onionServiceId`, `algorithm`, `canonicalization`.

### Recuperação emergencial

| Endpoint | Auth | Para que serve | Body | Response | Status relevantes |
|---|---|---|---|---|---|
| `POST /auth/recovery/emergency/start` | PUBLIC | Inicia recuperação de conta. | `EmergencyRecoveryStartRequest` | `EmergencyRecoveryStartResponse` | `202`, `400`, `401`, `429` |
| `POST /auth/recovery/emergency/finish` | PUBLIC | Finaliza recuperação com novo fator/credencial. | `EmergencyRecoveryFinishRequest` | `EmergencyRecoveryFinishResponse` | `200`, `400`, `401`, `410` |

`EmergencyRecoveryStartRequest` aceita `username`, `newPassphrase`, `recoveryCodes`, `challenge`, `nonce`. `EmergencyRecoveryFinishRequest` aceita `recoverySessionId`, `totpCode`, `publicKey`, `publicKeyCose`, `deviceName`, `signature`, `authData`, `clientDataJSON`, `credentialId`, `userHandle`.

### Acesso administrativo

| Endpoint | Auth | Para que serve | Params/body | Response |
|---|---|---|---|---|
| `POST /auth/admin/login` | PUBLIC | Inicia login admin com proof e device metadata. | `AdminLoginRequestDTO` | `AdminLoginResponseDTO` |
| `GET /auth/admin/login/{attemptId}` | PUBLIC | Poll de tentativa admin. | path `attemptId` UUID | `AdminLoginResponseDTO` |
| `POST /auth/admin/key` | ADMIN | Cria/rotaciona chave admin. | `AdminKeyCreateRequestDTO` | `AdminKeyStatusDTO` |
| `GET /auth/admin/key` | ADMIN | Status da chave admin. | nenhum | `AdminKeyStatusDTO` |
| `DELETE /auth/admin/key` | ADMIN | Revoga chave admin. | nenhum | `AdminKeyStatusDTO` |
| `GET /auth/admin/access-attempts/pending` | ADMIN | Lista tentativas pendentes. | nenhum | `List<AdminAccessAttemptDTO>` |
| `POST /auth/admin/access-attempts/{attemptId}/decision` | ADMIN | Aprova/rejeita tentativa. | path `attemptId`; body `decision` | `AdminAccessAttemptDTO` |
| `GET /auth/admin/devices` | ADMIN | Lista devices admin. | nenhum | `List<AdminAuthenticatedDeviceDTO>` |
| `POST /auth/admin/devices/{deviceId}/block` | ADMIN | Bloqueia device admin. | path `deviceId` | `AdminAuthenticatedDeviceDTO` |
| `POST /auth/admin/devices/{deviceId}/revoke` | ADMIN | Revoga device admin. | path `deviceId` | `AdminAuthenticatedDeviceDTO` |

Status codes: `200` sucesso; `202` tentativa pendente ou aceita; `400` decisão/body inválido; `401` login inválido; `403` JWT sem role `ADMIN`; `404` tentativa/device inexistente.

## DTOs e Payloads

### `AccountActivationStatusDTO`

Fonte: [AccountActivationStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountActivationStatusDTO.java)

Campos observados no DTO:

- `boolean activated`
- `boolean canReceiveInbound`
- `boolean requiresActivationDeposit`
- `BigDecimal requiredAmountBtc`
- `String paymentLinkId`
- `String depositAddress`
- `String paymentStatus`
- `String warningMessage`
- `LocalDateTime activatedAt`

### `AccountSecurityProfileDTO`

Fonte: [AccountSecurityProfileDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountSecurityProfileDTO.java)

Campos observados no DTO:

- `AccountSecurityType accountSecurity`
- `Integer shamirTotalShares`
- `Integer shamirThreshold`
- `Integer multisigThreshold`
- `boolean passkeyAvailable`
- `boolean passkeyEnabledForTransactions`
- `AppPinStatusDTO appPin`
- `List<String> requiredFactors`
- `PasskeyInventoryDTO passkeys`

### `AccountSecurityStatusDTO`

Fonte: [AccountSecurityStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountSecurityStatusDTO.java)

Campos observados no DTO:

- `boolean passwordConfigured`
- `boolean passkeyRegistered`
- `boolean totpEnabled`
- `int backupCodesRemaining`
- `boolean unprotected`
- `String warningMessage`
- `boolean accountActivated`
- `boolean inboundEnabled`
- `PasskeyInventoryDTO passkeys`

### `AccountSecurityUpdateRequestDTO`

Fonte: [AccountSecurityUpdateRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountSecurityUpdateRequestDTO.java)

Campos observados no DTO:

- `accountSecurity: AccountSecurityType`
- `shamirTotalShares: Integer`
- `shamirThreshold: Integer`
- `multisigThreshold: Integer`

### `AdminAccessAttemptDTO`

Fonte: [AdminAccessAttemptDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminAccessAttemptDTO.java)

Campos observados no DTO:

- `UUID attemptId`
- `String status`
- `String deviceId`
- `String deviceName`
- `String browser`
- `String userAgent`
- `String ipFingerprint`
- `LocalDateTime requestedAt`
- `LocalDateTime expiresAt`

### `AdminAccessDecisionRequestDTO`

Fonte: [AdminAccessDecisionRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminAccessDecisionRequestDTO.java)

Campos observados no DTO:

- `decision: String`

### `AdminAuthenticatedDeviceDTO`

Fonte: [AdminAuthenticatedDeviceDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminAuthenticatedDeviceDTO.java)

Campos observados no DTO:

- `String deviceId`
- `String deviceName`
- `String browser`
- `String userAgent`
- `String status`
- `LocalDateTime firstAccessAt`
- `LocalDateTime lastAccessAt`

### `AdminKeyCreateRequestDTO`

Fonte: [AdminKeyCreateRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminKeyCreateRequestDTO.java)

Campos observados no DTO:

- `keyMaterialHash: String`
- `deviceInstallId: String`

### `AdminKeyStatusDTO`

Fonte: [AdminKeyStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminKeyStatusDTO.java)

Campos observados no DTO:

- `boolean configured`
- `String status`
- `String fingerprint`
- `LocalDateTime createdAt`
- `LocalDateTime revokedAt`

### `AdminLoginRequestDTO`

Fonte: [AdminLoginRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminLoginRequestDTO.java)

Campos observados no DTO:

- `username: String`
- `adminKeyProof: String`
- `deviceId: String`
- `deviceName: String`
- `browser: String`
- `userAgent: String`
- `platform: String`

### `AdminLoginResponseDTO`

Fonte: [AdminLoginResponseDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminLoginResponseDTO.java)

Campos observados no DTO:

- `String status`
- `boolean requiresMobileApproval`
- `UUID attemptId`
- `LocalDateTime expiresAt`
- `String token`
- `String message`

### `AppPinStatusDTO`

Fonte: [AppPinStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AppPinStatusDTO.java)

Campos observados no DTO:

- `boolean enabled`
- `boolean configured`
- `boolean locked`
- `int failedAttempts`
- `int remainingAttempts`
- `int maxAttempts`
- `int minPinLength`
- `int maxPinLength`
- `boolean resettableWithTotp`
- `boolean deviceScoped`
- `LocalDateTime lockedUntil`
- `LocalDateTime lastVerifiedAt`
- `LocalDateTime updatedAt`

### `BackupCodesStatusDTO`

Fonte: [BackupCodesStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/BackupCodesStatusDTO.java)

Campos observados no DTO:

- `boolean enabled`
- `int remainingCodes`
- `List<String> newlyGeneratedCodes`

### `ConfigureAppPinRequestDTO`

Fonte: [ConfigureAppPinRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/ConfigureAppPinRequestDTO.java)

Campos observados no DTO:

- `enabled: Boolean`
- `pin: String`
- `currentPin: String`
- `totpCode: String`

### `DeviceKeyChallengeResponse`

Fonte: [DeviceKeyChallengeResponse.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyChallengeResponse.java)

Campos observados no DTO:

- `String challengeId`
- `String challenge`
- `long expiresInSeconds`
- `String onionServiceId`
- `String algorithm`
- `String canonicalization`

### `DeviceKeyDeviceDTO`

Fonte: [DeviceKeyDeviceDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyDeviceDTO.java)

Campos observados no DTO:

- `String credentialId`
- `String deviceName`
- `String deviceInstallId`
- `String keyStorage`
- `String platform`
- `String browser`
- `String onionServiceId`
- `String status`
- `long counter`
- `LocalDateTime createdAt`
- `LocalDateTime lastUsedAt`
- `LocalDateTime revokedAt`
- `int protocolVersion`

### `DeviceKeyRegistrationRequest`

Fonte: [DeviceKeyRegistrationRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyRegistrationRequest.java)

Campos observados no DTO:

- `publicKey: String`
- `publicKeySha256: String`
- `credentialId: String`
- `userHandle: String`
- `deviceName: String`
- `deviceInstallId: String`
- `keyStorage: String`
- `platform: String`
- `browser: String`
- `brand: String`
- `model: String`
- `serialNumber: String`
- `signedPayload: String`
- `signature: String`

### `DeviceKeyVerifyRequest`

Fonte: [DeviceKeyVerifyRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyVerifyRequest.java)

Campos observados no DTO:

- `username: String`
- `credentialId: String`
- `deviceInstallId: String`
- `signedPayload: String`
- `signature: String`

### `EmergencyRecoveryFinishRequest`

Fonte: [EmergencyRecoveryFinishRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryFinishRequest.java)

Campos observados no DTO:

- `recoverySessionId: String`
- `totpCode: String`
- `publicKey: String`
- `publicKeyCose: String`
- `deviceName: String`
- `signature: String`
- `authData: String`
- `clientDataJSON: String`
- `credentialId: String`
- `userHandle: String`

### `EmergencyRecoveryFinishResponse`

Fonte: [EmergencyRecoveryFinishResponse.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryFinishResponse.java)

Campos observados no DTO:

- `username: String`
- `newBackupCodes: List<String>`

### `EmergencyRecoveryStartRequest`

Fonte: [EmergencyRecoveryStartRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryStartRequest.java)

Campos observados no DTO:

- `username: String`
- `recoveryCodes: List<String>`
- `challenge: String`
- `nonce: String`

### `EmergencyRecoveryStartResponse`

Fonte: [EmergencyRecoveryStartResponse.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryStartResponse.java)

Campos observados no DTO:

- `recoverySessionId: String`
- `otpUri: String`
- `passkeyChallenge: String`
- `expiresInSeconds: long`
- `requiredRecoveryCodes: int`

### `PasskeyInventoryDTO`

Fonte: [PasskeyInventoryDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/PasskeyInventoryDTO.java)

Campos observados no DTO:

- `boolean passkeyRegistered`
- `boolean compatibleForCurrentLogin`
- `boolean legacyCredentialsPresent`
- `String currentRelyingPartyId`
- `String currentHost`
- `List<PasskeyDeviceDTO> devices`

### `PasskeyRegistrationRequest`

Fonte: [PasskeyRegistrationRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/passkey/PasskeyRegistrationRequest.java)

Campos observados no DTO:

- `publicKey: String`
- `deviceName: String`
- `signature: String`
- `authData: String`
- `clientDataJSON: String`
- `credentialId: String`
- `userHandle: String`
- `publicKeyCose: String`
- `brand: String`
- `model: String`
- `serialNumber: String`
- `deviceInstallId: String`
- `platform: String`
- `browser: String`
- `status: String`

### `PasskeyVerifyRequest`

Fonte: [PasskeyVerifyRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/passkey/PasskeyVerifyRequest.java)

Campos observados no DTO:

- `username: String`
- `signature: String`
- `authData: String`
- `clientDataJSON: String`
- `credentialId: String`

### `SignupResponseDTO`

Fonte: [SignupResponseDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/SignupResponseDTO.java)

Campos observados no DTO:

- `sessionId: String`
- `otpUri: String`
- `backupCodes: List<String>`
- `totpOptional: boolean`

### `SignupTotpVerifyRequestDTO`

Fonte: [SignupTotpVerifyRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/SignupTotpVerifyRequestDTO.java)

Campos observados no DTO:

- `sessionId: String`
- `totpCode: String`

### `TotpSetupResponseDTO`

Fonte: [TotpSetupResponseDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/TotpSetupResponseDTO.java)

Campos observados no DTO:

- `String otpUri`
- `String secret`

### `UserDTO`

Fonte: [UserDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/UserDTO.java)

Campos observados no DTO:

- `username: String`
- `totpSecret: String`
- `totpCode: String`
- `voucherCode: String`
- `challenge: String`
- `nonce: String`
- `preAuthToken: String`
- `sessionId: String`
- `accountSecurity: AccountSecurityType`
- `shamirTotalShares: Integer`
- `shamirThreshold: Integer`
- `multisigThreshold: Integer`
- `backupCodes: java.util.List<String>`

### `VerifyAppPinRequestDTO`

Fonte: [VerifyAppPinRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/VerifyAppPinRequestDTO.java)

Campos observados no DTO:

- `pin: String`


## Notas Operacionais

- `X-Device-Hash` e opcional e usado para escopo/estado local de dispositivo em `me`, perfil de seguranca e App PIN.
- Endpoints publicos de onboarding/passkey/device-key ainda podem retornar `401`, `404`, `409` ou `400` conforme validacoes internas, mesmo quando a rota em si e PUBLIC.

## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.
