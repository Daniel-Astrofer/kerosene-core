# Referencia da API a partir dos Controllers

Consulte [README.md](README.md) para a navegacao completa da documentacao do servico.

## Escopo

Esta documentacao foi extraida do codigo-fonte dos controllers em `src/main/java/source/**`, da configuracao de seguranca em [Security.java](/home/omega/Kerosene/backend/kerosene/src/main/java/source/auth/application/infra/security/Security.java), e dos handlers de erro globais.

Regras usadas nesta leitura:

- A autenticacao foi documentada conforme a `SecurityFilterChain`, mesmo quando algum comentario do controller chama a rota de "publica".
- Os status de erro listados por endpoint vieram de `return` explicito, `GlobalExceptionHandler`, `RestResponseErrors`, `ResponseStatusException` e filtros de seguranca.
- Salvo quando a linha informar outro comportamento, excecoes nao tratadas acabam em `500 ApiErr`.
- Falta de `@RequestParam`, `@PathVariable`, `@RequestBody` obrigatorio, falha de desserializacao e `@Valid` caem em `400 SpringErr`.

## Envelopes e formatos de resposta

### `ApiOk<T>` e `ApiErr`

Quase toda a API usa `ApiResponse<T>`:

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `success` | `boolean` | `true` em sucesso, `false` em erro. |
| `message` | `string` | Mensagem de negocio. |
| `data` | `T` ou `null` | Payload do endpoint. |
| `errorCode` | `string` ou `null` | Codigo de erro padrao do backend. |
| `timestamp` | `LocalDateTime` | Gerado no servidor. |

Abreviacoes usadas nas tabelas:

- `ApiOk<T>` = `ApiResponse<T>`
- `ApiErr` = `ApiResponse<Void>` ou `ApiResponse<null>`
- `LegacyErr` = `ResponseError`
- `Map` = JSON bruto montado pelo controller
- `SpringErr` = erro padrao do Spring MVC / Spring Boot

### `LegacyErr`

O projeto ainda tem um `@ControllerAdvice` legado em `RestResponseErrors`, entao alguns erros de autenticacao, carteira e replay podem sair neste formato:

| Campo | Tipo |
| --- | --- |
| `timestamp` | `LocalDateTime` |
| `status` | `HttpStatus` |
| `error` | `string` |
| `message` | `string` |
| `path` | `string` |

### Regras de autenticacao observadas

- Rotas marcadas como `Publico` estao liberadas em `Security.java`.
- Rotas marcadas como `JWT` exigem `Authorization: Bearer <token>`.
- Rotas `JWT + X-Admin-Token` exigem JWT e o header adicional.
- Rotas `JWT + ROLE_ADMIN` exigem JWT e `@PreAuthorize("hasRole('ADMIN')")`.

Observacao importante:

- Em rotas protegidas, token invalido pode virar `401 ApiErr` ou `401 LegacyErr`, porque `JwtAuthenticationFilter` lanca `AuthExceptions.InvalidCredentials` e o projeto tem dois `ControllerAdvice` concorrentes.
- Em rotas protegidas sem credencial, o bloqueio acontece antes do controller. Na pratica, a aplicacao pode responder com `403` do Spring Security.

## Endpoints

### Auth: `UsuarioController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /auth/pow/challenge` | Publico | sem body | `200 ApiOk<PowChallengeData>` | - | `data.challenge` |
| `POST /auth/login` | Publico | body `UserDTO.login` | `202 ApiOk<String>` | `400/401/403/404/408` via handlers de auth | `data` = `preAuthToken` |
| `POST /auth/signup` | Publico | body `UserDTO.signup` | `200 ApiOk<SignupResponseDTO>` | `400/401/409` via handlers de auth | `data.otpUri`, `data.backupCodes[]` |
| `POST /auth/signup/totp/verify` | Publico | body `UserDTO.signupTotpVerify` | `202 ApiOk<String>` | `400/401/408` via handlers de auth | `data` = `sessionId` de onboarding |
| `POST /auth/login/totp/verify` | Publico | body `UserDTO.loginTotpVerify` | `202 ApiOk<String>` | `401/403/408` via handlers de auth | `data` = string no formato `"<userId> <jwt>"` |

### Auth: `PasskeyController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /auth/passkey/challenge` | Publico | query `username:string` | `200 ApiOk<String>` | - | `data` = challenge WebAuthn/passkey |
| `POST /auth/passkey/register` | JWT | body `PasskeyRegistrationRequest` | `200 ApiOk<String>` | `400 ApiErr`; `401 ApiErr` challenge expirada/assinatura invalida; `404 ApiErr` usuario nao encontrado | `data` = `"OK"` |
| `POST /auth/passkey/verify` | Publico | body `PasskeyVerifyRequest` | `200 ApiOk<String>` | `400 ApiErr`; `401 ApiErr`; `404 ApiErr` | `data` = JWT |
| `POST /auth/passkey/onboarding/start` | Publico | query `sessionId:string` | `200 ApiOk<String>` | `404 ApiErr` sessao expirada | `data` = challenge de onboarding |
| `POST /auth/passkey/onboarding/finish` | Publico | query `sessionId:string`; body `PasskeyRegistrationRequest` | `200 ApiOk<String>` | `400 ApiErr`; `401 ApiErr`; `404 ApiErr` | `data` = `"OK"` |

### Auth: `AccountSecurityController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /auth/security/profile` | JWT | sem body | `200 ApiOk<AccountSecurityProfileDTO>` | `401 ApiErr/LegacyErr` contexto invalido | perfil de seguranca da conta |
| `PUT /auth/security/profile` | JWT | body `AccountSecurityUpdateRequestDTO` | `200 ApiOk<AccountSecurityProfileDTO>` | `400 ApiErr`; `401 ApiErr/LegacyErr` | perfil atualizado |

### Auth: `EmergencyRecoveryController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `POST /auth/recovery/emergency/start` | Publico | body `EmergencyRecoveryStartRequest` | `202 ApiOk<EmergencyRecoveryStartResponse>` | `400 ApiErr` (`RECOVERY_BAD_REQUEST`); `401 ApiErr` (`RECOVERY_REJECTED`); `429 ApiErr` (`RECOVERY_RATE_LIMITED`) | sessao de recovery, novo `otpUri`, challenge de passkey |
| `POST /auth/recovery/emergency/finish` | Publico | body `EmergencyRecoveryFinishRequest` | `200 ApiOk<EmergencyRecoveryFinishResponse>` | `400 ApiErr` (`RECOVERY_BAD_REQUEST`); `401 ApiErr` (`RECOVERY_REJECTED`); `410 ApiErr` (`RECOVERY_SESSION_EXPIRED`) | usuario e novos backup codes |

### Wallet: `WalletController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `POST /wallet/create` | JWT | body `WalletRequestDTO` | `201 ApiOk<WalletResponseDTO>` | `400 SpringErr` validacao; `400 ApiErr`; `409 ApiErr/LegacyErr` nome duplicado | carteira criada |
| `GET /wallet/all` | JWT | sem body | `200 ApiOk<List<WalletResponseDTO>>` | - | lista de carteiras do usuario |
| `GET /wallet/find` | JWT | query `name:string` | `200 ApiOk<WalletResponseDTO>` | `404 ApiErr/LegacyErr` | carteira encontrada |
| `PUT /wallet/update` | JWT | body `WalletUpdateDTO` | `200 ApiOk<String>` | `400 SpringErr`; `400 ApiErr`; `404 ApiErr/LegacyErr`; `409 ApiErr/LegacyErr` | `data = null`; sucesso via `message` |
| `DELETE /wallet/delete` | JWT | body `WalletRequestDTO` | `200 ApiOk<String>` | `400 ApiErr`; `404 ApiErr/LegacyErr` | `data = null`; sucesso via `message` |

### Ledger: `LedgerController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `POST /ledger/transaction` | JWT | body `TransactionDTO` | `200 ApiOk<InternalTransactionResponseDTO>` | `400 ApiErr`; `402 ApiErr`; `404 ApiErr`; `409 LegacyErr` idempotency duplicada; `422 LegacyErr` replay/rate-limit | transacao interna processada |
| `GET /ledger/history` | JWT | query `page:int=0`; `size:int=50` | `200 ApiOk<List<LedgerTransactionHistory>>` | - | historico paginado, `size` limitado a `100` |
| `GET /ledger/all` | JWT | sem body | `200 ApiOk<List<LedgerDTO>>` | - | todos os ledgers do usuario |
| `GET /ledger/find` | JWT | query `walletName:string` | `200 ApiOk<LedgerDTO>` | `404 ApiErr` | ledger por nome da carteira |
| `GET /ledger/balance` | JWT | query `walletName:string` | `200 ApiOk<BigDecimal>` | `404 ApiErr` | saldo do ledger em BTC |
| `POST /ledger/payment-request` | JWT | body `CreatePaymentRequestReq` | `200 ApiOk<InternalPaymentRequestDTO>` | `400 ApiErr`; `404 ApiErr` | payment request interno completo |
| `GET /ledger/payment-request/{linkId}` | JWT | path `linkId:string` | `200 ApiOk<PaymentRequestPublicDTO>` | `404 ApiErr` | visao publica do payment request |
| `POST /ledger/payment-request/{linkId}/pay` | JWT | path `linkId:string`; body `PayPaymentRequestReq` | `200 ApiOk<InternalPaymentRequestDTO>` | `400 ApiErr`; `402 ApiErr`; `403 ApiErr`; `404 ApiErr`; `409 ApiErr`; `410 ApiErr`; `422 LegacyErr` | request paga |

Observacao:

- O comentario de codigo diz que `GET /ledger/payment-request/{linkId}` seria publico, mas a configuracao real de seguranca exige JWT para `/ledger/**`.

### Audit: `LedgerAuditController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /v1/audit/stats` | JWT | sem body | `200 Map` | - | `TransparencyStatsResponse` |
| `GET /v1/audit/config` | JWT + `X-Admin-Token` | header `X-Admin-Token:string` | `200 TreasuryAuditConfigResponseDTO` | `403 Map` token invalido | configuracao global de auditoria |
| `PUT /v1/audit/config` | JWT + `X-Admin-Token` | header `X-Admin-Token:string`; body `TreasuryAuditConfigRequestDTO` | `200 TreasuryAuditConfigResponseDTO` | `400 SpringErr` `ResponseStatusException`; `403 Map` token invalido | configuracao atualizada |
| `POST /v1/audit/siphon` | JWT + headers de dono | headers `X-Owner-TOTP:string`, `X-Hardware-Signature:string`; body `Map<String,String>` | `200 Map` | `400 Map` sem taxas; `403 Map` TOTP/hardware invalido | resultado do saque de fees |

### Audit: `MerkleAuditController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /audit/latest-root` | JWT | sem body | `200 Map` | - | `MerkleCheckpoint`; quando vazio, retorna sentinela `NO_CHECKPOINT_YET` |
| `GET /audit/history` | JWT | query `limit:int=10` | `200 List<Map>` | - | lista de `MerkleCheckpoint`, maximo `50` |
| `POST /audit/trigger` | JWT + `ROLE_ADMIN` | sem body | `200 Map` | `403` se nao for admin | novo `MerkleCheckpoint` |

### Transactions: `TransactionController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /transactions/deposit-address` | JWT | sem body | `200 ApiOk<String>` | `503 ApiErr` custody provider indisponivel; `500` se usuario nao tiver carteira primaria configurada | `data` = endereco on-chain custodial dedicado |
| `GET /transactions/estimate-fee` | JWT | query `amount:decimal` | `200 ApiOk<EstimatedFeeDTO>` | `400 SpringErr/ApiErr` | estimativa de fee |
| `POST /transactions/create-unsigned` | JWT | body `TransactionRequestDTO` | `200 ApiOk<UnsignedTransactionDTO>` | `400 ApiErr` | transacao bruta nao assinada |
| `GET /transactions/status` | JWT | query `txid:string` | `200 ApiOk<TransactionResponseDTO>` | `400 SpringErr/ApiErr` | status da transacao |
| `POST /transactions/broadcast` | JWT | body `BroadcastTransactionDTO` | `200 ApiOk<TransactionResponseDTO>` | `400 ApiErr`; `502 ApiErr` broadcast falhou | transacao transmitida |
| `POST /transactions/create-payment-link` | JWT | body `CreatePaymentLinkRequest` | `201 ApiOk<PaymentLinkDTO>` | `400 ApiErr` | payment link criado |
| `GET /transactions/payment-link/{linkId}` | JWT | path `linkId:string` | `200 ApiOk<PaymentLinkDTO>` | `404 ApiErr` | payment link autenticado |
| `POST /transactions/payment-link/{linkId}/confirm` | JWT | path `linkId:string`; body `ConfirmPaymentRequest` | `200 ApiOk<PaymentLinkDTO>` | `400 ApiErr`; `404 ApiErr`; `409 ApiErr`; `410 ApiErr` | payment link confirmado |
| `POST /transactions/payment-link/{linkId}/complete` | JWT | path `linkId:string` | `200 ApiOk<PaymentLinkDTO>` | `404 ApiErr`; `409 ApiErr`; `410 ApiErr` | payment link concluido |
| `GET /transactions/payment-links` | JWT | sem body | `200 ApiOk<List<PaymentLinkDTO>>` | - | links do usuario |
| `POST /transactions/withdraw` | JWT | body `WithdrawRequestDTO` | `200 ApiOk<TransactionResponseDTO>` | `400 ApiErr`; `402 ApiErr`; `404 ApiErr`; `502 ApiErr`; `503 ApiErr` | saque on-chain |

### Transactions: `NetworkPaymentsController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `POST /transactions/network/onchain/address` | JWT | body `OnchainAddressRequestDTO` | `201 ApiOk<WalletNetworkAddressDTO>` | `404 ApiErr`; `503 ApiErr` | perfil de rede com endereco on-chain |
| `GET /transactions/network/wallet-profile` | JWT | query `walletName:string` | `200 ApiOk<WalletNetworkAddressDTO>` | `404 ApiErr` | perfil de rede da carteira |
| `POST /transactions/network/onchain/send` | JWT | body `OnchainSendRequestDTO` | `200 ApiOk<ExternalTransferResponseDTO>` | `400 ApiErr`; `402 ApiErr`; `404 ApiErr`; `503 ApiErr` | transferencia externa on-chain |
| `POST /transactions/network/lightning/invoice` | JWT | body `LightningInvoiceRequestDTO` | `201 ApiOk<LightningInvoiceResponseDTO>` | `400 ApiErr`; `404 ApiErr`; `503 ApiErr` | invoice Lightning criada |
| `POST /transactions/network/transfers/{transferId}/cancel` | JWT | path `transferId:UUID` | `200 ApiOk<ExternalTransferResponseDTO>` | `404 ApiErr`; `409 ApiErr` | transferencia cancelada |
| `POST /transactions/network/lightning/pay` | JWT | body `LightningPaymentRequestDTO` | `200 ApiOk<ExternalTransferResponseDTO>` | `400 ApiErr`; `402 ApiErr`; `404 ApiErr`; `503 ApiErr` | pagamento Lightning enviado |
| `GET /transactions/network/transfers` | JWT | sem body | `200 ApiOk<List<ExternalTransferResponseDTO>>` | - | historico de transferencias externas |
| `GET /transactions/network/transfers/{transferId}` | JWT | path `transferId:UUID` | `200 ApiOk<ExternalTransferResponseDTO>` | `404 ApiErr` | transferencia externa especifica |

### Transactions: `EconomyController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /api/economy/status` | JWT | sem body | `200 ApiOk<EconomyStatusData>` | - | fee de saque e status de withdrawals |
| `GET /api/economy/btc-price` | JWT | sem body | `200 ApiOk<BtcPriceData>` | - | cotacoes `btcUsd`, `btcBrl`, `usdBrl` |

### Transactions: `OnrampController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /api/onramp/urls` | JWT | query opcional `walletName:string`, `amountBtc:decimal` | `200 ApiOk<OnrampUrlsData>` | `400 ApiErr` (`ONRAMP_ERROR`); `500 ApiErr` (`SERVER_ERROR`) | URLs de MoonPay, Banxa e Bipa com endereco dedicado |

### Mining: `MiningController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /mining/rigs` | JWT | sem body | `200 ApiOk<List<MiningRigOfferDTO>>` | - | marketplace de rigs |
| `POST /mining/allocations` | JWT | body `MiningAllocationRequestDTO` | `201 ApiOk<MiningAllocationResponseDTO>` | `400 ApiErr`; `402 ApiErr`; `404 ApiErr`; `409 ApiErr` | alocacao criada |
| `GET /mining/allocations` | JWT | sem body | `200 ApiOk<List<MiningAllocationResponseDTO>>` | - | alocacoes do usuario |
| `GET /mining/allocations/{allocationId}` | JWT | path `allocationId:UUID` | `200 ApiOk<MiningAllocationResponseDTO>` | `404 ApiErr` | alocacao especifica |
| `POST /mining/allocations/{allocationId}/cancel` | JWT | path `allocationId:UUID` | `200 ApiOk<MiningAllocationResponseDTO>` | `404 ApiErr`; `409 ApiErr` | alocacao cancelada |

### Notifications: `NotificationController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `POST /notifications/send` | JWT | body `NotificationSendRequest` | `200 ApiOk<String>` | `400 ApiErr` campos ausentes ou `userId` invalido | `data = null`; sucesso via `message` |

### Voucher: `VoucherController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `POST /voucher/request` | Publico | sem body | `200 ApiOk<VoucherRequestDataResponse>` | - | endereco e valor de deposito do voucher |
| `POST /voucher/confirm` | Publico | query `pendingVoucherId:string`, `txid:string` | `200 ApiOk<String>` | `400 ApiErr` (`VOUCHER_ERROR`) | `data` = codigo do voucher |
| `POST /voucher/onboarding-link` | Publico | query `sessionId:string` | `200 ApiOk<PaymentLinkDTO>` | `400 ApiErr` sessao/passkey; `500 ApiErr` (`ONBOARDING_SERVER_ERROR`) | payment link fixo de onboarding |
| `GET /voucher/onboarding-link/{linkId}` | Publico | path `linkId:string` | `200 ApiOk<PaymentLinkDTO>` | `404 ApiErr` | status publico do onboarding link |
| `POST /voucher/onboarding-link/{linkId}/confirm` | Publico | path `linkId:string`; body `ConfirmPaymentRequest` | `200 ApiOk<PaymentLinkDTO>` | `400 ApiErr` (`ONBOARDING_CONFIRM_ERROR`) | onboarding payment confirmado |
| `POST /voucher/onboarding-mock-confirm` | Publico | query `sessionId:string` | `200 ApiOk<String>` | `400 ApiErr` (`MOCK_ERROR`) | `data` = `"OK"` |

### Sovereignty: `SovereigntyStatusController`

| Endpoint | Auth | Entrada | Sucesso | Erros relevantes | Body de resposta |
| --- | --- | --- | --- | --- | --- |
| `GET /sovereignty/status` | JWT | sem body | `200 Map` | - | `SovereigntyStatusResponse` |
| `POST /sovereignty/reattest` | JWT + `X-Admin-Token` | header `X-Admin-Token:string` | `200 Map` | `403 Map` token invalido; `503 Map` token nao configurado | resposta simples de re-attestation |
| `GET /sovereignty/telemetry` | JWT + `X-Admin-Token` | header `X-Admin-Token:string` | `200 Map` | `403 Map` token invalido | `TelemetrySnapshot` |
| `GET /sovereignty/ping` | JWT | sem body | `200 text/html` | - | pagina HTML de health/ping |

Observacao:

- Os comentarios do controller descrevem `/sovereignty/**` como publico, mas a seguranca atual exige JWT para essas rotas.

## Schemas de request e response

### Schemas de auth

#### `UserDTO.login`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `username` | `string` | sim | Normalizado para lowercase pelo backend. |
| `passphrase` | `string` | sim | Frase BIP39. |

#### `UserDTO.signup`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `username` | `string` | sim | Validado por formato e tamanho. |
| `passphrase` | `string` | sim | Deve passar nas regras BIP39. |
| `challenge` | `string` | sim | Challenge de PoW obtido em `/auth/pow/challenge`. |
| `nonce` | `string` | sim | Nonce calculado pelo cliente para o PoW. |
| `voucherCode` | `string` | nao | Presente no DTO, mas nao e usado diretamente pelo controller. |
| `accountSecurity` | `enum` | nao | `STANDARD`, `SHAMIR`, `MULTISIG_2FA`, `PASSKEY`. |
| `shamirTotalShares` | `integer` | condicional | Obrigatorio quando `accountSecurity = SHAMIR`; intervalo `2..8`. |
| `shamirThreshold` | `integer` | condicional | Obrigatorio quando `accountSecurity = SHAMIR`; intervalo `2..shamirTotalShares`. |
| `multisigThreshold` | `integer` | condicional | Em `MULTISIG_2FA`, aceita `2` ou `3`; default `2`. |

#### `UserDTO.signupTotpVerify`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `username` | `string` | sim | Usado para localizar o cadastro temporario no Redis. |
| `totpCode` | `string` | sim | Codigo TOTP da seed retornada no signup. |

#### `UserDTO.loginTotpVerify`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `preAuthToken` | `string` | sim | Retornado em `/auth/login`. |
| `totpCode` | `string` | sim | TOTP ou backup code de 8 digitos. |

#### `SignupResponseDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `otpUri` | `string` | URI para QR Code TOTP. |
| `backupCodes` | `string[]` | 10 codigos brutos retornados ao cliente uma unica vez. |

#### `PasskeyRegistrationRequest`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `publicKey` | `string` | condicional | Fallback Base64/Base64URL da chave publica. |
| `publicKeyCose` | `string` | condicional | Preferencial; Base64/Base64URL do COSE key. |
| `deviceName` | `string` | nao | Nome amigavel do dispositivo. |
| `signature` | `string` | sim | Assinatura da challenge. |
| `authData` | `string` | sim | `authenticatorData` do WebAuthn. |
| `clientDataJSON` | `string` | sim | `clientDataJSON` do WebAuthn. |
| `credentialId` | `string` | nao | Base64/Base64URL. |
| `userHandle` | `string` | nao | Base64/Base64URL. |

Observacao:

- Em `/auth/passkey/onboarding/finish`, pelo menos um entre `publicKeyCose` e `publicKey` precisa existir.

#### `PasskeyVerifyRequest`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `username` | `string` | sim | Usuario dono da credencial. |
| `credentialId` | `string` | sim | Necessario para lookup seguro da credencial. |
| `signature` | `string` | sim | Assinatura do challenge. |
| `authData` | `string` | sim | `authenticatorData`. |
| `clientDataJSON` | `string` | sim | `clientDataJSON`. |

#### `AccountSecurityUpdateRequestDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `accountSecurity` | `enum` | nao | Default `STANDARD`. |
| `shamirTotalShares` | `integer` | condicional | Obrigatorio para `SHAMIR`. |
| `shamirThreshold` | `integer` | condicional | Obrigatorio para `SHAMIR`. |
| `multisigThreshold` | `integer` | condicional | Em `MULTISIG_2FA`, aceita `2` ou `3`; `3` exige passkey registrada. |

#### `AccountSecurityProfileDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `accountSecurity` | `enum` | Modo atual da conta. |
| `shamirTotalShares` | `integer` | Configuracao SHAMIR. |
| `shamirThreshold` | `integer` | Configuracao SHAMIR. |
| `multisigThreshold` | `integer` | Threshold efetivo. |
| `passkeyAvailable` | `boolean` | Existe passkey cadastrada. |
| `passkeyEnabledForTransactions` | `boolean` | Flag persistida no usuario. |
| `requiredFactors` | `string[]` | Fatores exigidos pelo modo atual. |

#### `EmergencyRecoveryStartRequest`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `username` | `string` | sim | Usuario a recuperar. |
| `newPassphrase` | `string` | sim | Nova passphrase; nao pode ser igual a atual. |
| `recoveryCodes` | `string[]` | sim | Minimo configuravel de codigos distintos; padrao do codigo: 3. |
| `challenge` | `string` | sim | PoW challenge. |
| `nonce` | `string` | sim | Nonce do PoW. |

#### `EmergencyRecoveryStartResponse`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `recoverySessionId` | `string` | ID da sessao de recovery. |
| `otpUri` | `string` | URI da nova seed TOTP. |
| `passkeyChallenge` | `string` | Challenge para registrar a nova passkey. |
| `expiresInSeconds` | `long` | TTL da sessao. |
| `requiredRecoveryCodes` | `integer` | Quantidade exigida. |

#### `EmergencyRecoveryFinishRequest`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `recoverySessionId` | `string` | sim | Sessao aberta no passo anterior. |
| `totpCode` | `string` | sim | Codigo TOTP da nova seed. |
| `publicKey` | `string` | condicional | Fallback da chave publica. |
| `publicKeyCose` | `string` | condicional | Preferencial. |
| `deviceName` | `string` | sim | Nome do dispositivo. |
| `signature` | `string` | sim | Assinatura da challenge de recovery. |
| `authData` | `string` | sim | `authenticatorData`. |
| `clientDataJSON` | `string` | sim | `clientDataJSON`. |
| `credentialId` | `string` | sim | Credencial da nova passkey. |
| `userHandle` | `string` | nao | Opcional. |

#### `EmergencyRecoveryFinishResponse`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `username` | `string` | Usuario recuperado. |
| `newBackupCodes` | `string[]` | Novos backup codes brutos. |

### Schemas de wallet

#### `WalletRequestDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `passphrase` | `string` | sim | Obrigatoria. |
| `name` | `string` | sim | Entre 3 e 50 caracteres. |
| `xpub` | `string` | nao | XPUB opcional. |

#### `WalletUpdateDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `passphrase` | `string` | sim | Autoriza a alteracao. |
| `name` | `string` | sim | Nome atual da carteira. |
| `newName` | `string` | nao | Novo nome. |
| `newXpub` | `string` | nao | Novo XPUB. |

#### `WalletResponseDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `long` | ID da carteira. |
| `name` | `string` | Nome. |
| `passphraseHash` | `string` | Hash persistido da passphrase; atualmente exposto pela API. |
| `createdAt` | `datetime` | Data de criacao. |
| `updatedAt` | `datetime` | Data de atualizacao. |
| `isActive` | `boolean` | Carteira ativa. |
| `totpUri` | `string` | URI TOTP associada, se houver. |
| `depositAddress` | `string` | Endereco de deposito. |
| `lightningAddress` | `string` | Endereco Lightning. |
| `xpubConfigured` | `boolean` | Se ha XPUB configurado. |
| `cardType` | `string` | Perfil/tipo do cartao. |
| `withdrawalFeeRate` | `decimal` | Fee de saque. |
| `depositFeeRate` | `decimal` | Fee de deposito. |

### Schemas de ledger e auditoria

#### `TransactionDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `sender` | `string` | sim | Username, ID numerico de carteira ou endereco BTC. |
| `receiver` | `string` | sim | Username, ID numerico de carteira ou endereco BTC. |
| `amount` | `decimal` | sim | Valor da transacao interna. |
| `context` | `string` | nao | Contexto descritivo. |
| `idempotencyKey` | `string` | nao | UUID do cliente para idempotencia. |
| `requestTimestamp` | `long` | nao | Epoch ms para anti-replay. |
| `passkeyAssertionJson` | `string` | condicional | Exigido quando transacoes usam passkey. |
| `confirmationPassphrase` | `string` | condicional | Exigido em modos com coassinatura/confirmacao adicional. |
| `totpCode` | `string` | condicional | Fator extra de seguranca. |

#### `InternalTransactionResponseDTO`

| Campo | Tipo |
| --- | --- |
| `txid` | `string` |
| `status` | `string` |
| `amount` | `decimal` |
| `sender` | `string` |
| `receiver` | `string` |
| `context` | `string` |

#### `LedgerTransactionHistory`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `UUID` | ID do historico. |
| `senderIdentifier` | `string` | Remetente original. |
| `senderUserId` | `long` | Usuario remetente. |
| `receiverIdentifier` | `string` | Destinatario original. |
| `receiverUserId` | `long` | Usuario destinatario. |
| `transactionType` | `string` | Ex.: `INTERNAL`, `EXTERNAL_DEPOSIT`, `EXTERNAL_WITHDRAWAL`. |
| `amount` | `decimal` | Valor. |
| `status` | `string` | Ex.: `PENDING`, `CONCLUDED`, `CANCELED`. |
| `networkFee` | `decimal` | Fee de rede. |
| `blockchainTxid` | `string` | TXID on-chain, se houver. |
| `context` | `string` | Contexto textual. |
| `createdAt` | `datetime` | Data de criacao. |
| `confirmations` | `integer` | Confirmacoes blockchain. |

#### `LedgerDTO`

| Campo | Tipo |
| --- | --- |
| `id` | `integer` |
| `walletId` | `long` |
| `walletName` | `string` |
| `balance` | `decimal` |
| `nonce` | `integer` |
| `lastHash` | `string` |
| `context` | `string` |
| `amount` | `decimal` |

#### `CreatePaymentRequestReq`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `amount` | `decimal` | sim |
| `receiverWalletName` | `string` | sim |

#### `PayPaymentRequestReq`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `payerWalletName` | `string` | sim | Carteira pagadora. |
| `totpCode` | `string` | condicional | Fator adicional. |
| `passkeyAssertionJson` | `string` | condicional | Prova WebAuthn. |
| `confirmationPassphrase` | `string` | condicional | Confirmacao por passphrase. |

#### `InternalPaymentRequestDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `string` | ID do link. |
| `requesterUserId` | `long` | Usuario que criou. |
| `receiverWalletId` | `long` | Carteira recebedora. |
| `receiverWalletName` | `string` | Nome da carteira recebedora. |
| `destinationHash` | `string` | Hash/endereco de destino. |
| `amount` | `decimal` | Valor. |
| `status` | `string` | Ex.: `PENDING`, `PAID`, `CANCELED`. |
| `expiresAt` | `datetime` | Expiracao. |
| `createdAt` | `datetime` | Criacao. |
| `paidAt` | `datetime` | Pagamento. |

#### `PaymentRequestPublicDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `string` | ID do link. |
| `amount` | `decimal` | Valor. |
| `status` | `string` | Status atual. |
| `expiresAt` | `datetime` | Expiracao. |
| `destinationHash` | `string` | Destino. |
| `locked` | `boolean` | Sempre `true` por default no DTO. |

#### `TreasuryAuditConfigRequestDTO`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `maxWithdrawLimit` | `decimal` | nao |
| `auditXpub` | `string` | nao |

#### `TreasuryAuditConfigResponseDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `maxWithdrawLimit` | `decimal` | Limite global de saque. |
| `auditXpubConfigured` | `boolean` | Existe XPUB de auditoria. |
| `auditXpubPreview` | `string` | XPUB abreviado. |
| `updatedAt` | `datetime` | Ultima atualizacao. |

#### `TransparencyStatsResponse`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `liability_to_users` | `decimal` | Passivo para usuarios. |
| `platform_profit_pending` | `decimal` | Lucro pendente. |
| `actual_onchain_balance` | `decimal` | Saldo on-chain observado. |
| `actual_lightning_balance` | `decimal` | Saldo Lightning observado. |
| `actual_wallet_xpub_balance` | `decimal` | Saldo monitorado em XPUBs de wallet. |
| `actual_treasury_xpub_balance` | `decimal` | Saldo monitorado em XPUB de tesouraria. |
| `actual_total_assets` | `decimal` | Total de ativos. |
| `is_solvent` | `boolean` | So aparece quando `liability` e `actual_onchain_balance` existem. |

#### `MerkleCheckpoint`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `string` | UUID textual. |
| `merkleRoot` | `string` | Root hash. |
| `ledgerCount` | `long` | Quantidade de ledgers. |
| `createdAt` | `datetime` | Data da captura. |
| `anchorTxid` | `string` | TXID de ancoragem; vazio ou `null` quando nao existe. |

### Schemas de transacoes, payment links e rede

#### `TransactionRequestDTO`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `fromAddress` | `string` | sim |
| `toAddress` | `string` | sim |
| `amount` | `decimal` | sim |
| `feeSatoshis` | `long` | sim |

#### `UnsignedTransactionDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `rawTxHex` | `string` | Transacao bruta. |
| `txId` | `string` | TXID preliminar. |
| `inputs` | `TransactionInput[]` | UTXOs usados. |
| `outputs` | `TransactionOutput[]` | Saidas. |
| `totalAmount` | `decimal` | Total da transacao. |
| `fee` | `long` | Fee em satoshis. |
| `fromAddress` | `string` | Origem. |
| `toAddress` | `string` | Destino. |

`TransactionInput`:

| Campo | Tipo |
| --- | --- |
| `txid` | `string` |
| `vout` | `integer` |
| `value` | `decimal` |
| `scriptPubKey` | `string` |

`TransactionOutput`:

| Campo | Tipo |
| --- | --- |
| `address` | `string` |
| `value` | `decimal` |

#### `EstimatedFeeDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `fastSatoshisPerByte` | `long` | Confirmacao rapida. |
| `standardSatoshisPerByte` | `long` | Confirmacao padrao. |
| `slowSatoshisPerByte` | `long` | Confirmacao lenta. |
| `estimatedFastBtc` | `decimal` | Fee rapida em BTC. |
| `estimatedStandardBtc` | `decimal` | Fee padrao em BTC. |
| `estimatedSlowBtc` | `decimal` | Fee lenta em BTC. |
| `amountReceived` | `decimal` | Valor liquido recebido. |
| `totalToSend` | `decimal` | Total necessario incluindo fee. |

#### `TransactionResponseDTO`

| Campo | Tipo |
| --- | --- |
| `txid` | `string` |
| `status` | `string` |
| `feeSatoshis` | `long` |
| `amountReceived` | `decimal` |
| `sender` | `string` |
| `receiver` | `string` |
| `context` | `string` |

#### `BroadcastTransactionDTO`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `rawTxHex` | `string` | sim |
| `toAddress` | `string` | sim |
| `amount` | `decimal` | sim |
| `message` | `string` | nao |

#### `CreatePaymentLinkRequest`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `amount` | `decimal` | sim |
| `description` | `string` | nao |

#### `ConfirmPaymentRequest`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `txid` | `string` | sim |
| `fromAddress` | `string` | sim |

#### `PaymentLinkDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `string` | ID do link. |
| `userId` | `long` | Pode ser `null` em onboarding. |
| `sessionId` | `string` | Usado antes da persistencia do usuario. |
| `amountBtc` | `decimal` | Valor principal. |
| `grossAmountBtc` | `decimal` | Valor bruto. |
| `depositFeeBtc` | `decimal` | Fee de deposito. |
| `netAmountBtc` | `decimal` | Valor liquido. |
| `description` | `string` | Descricao. |
| `depositAddress` | `string` | Endereco de deposito. |
| `status` | `string` | Ex.: `pending`, `paid`, `expired`, `completed`. |
| `txid` | `string` | TXID associado. |
| `expiresAt` | `datetime` | Expiracao. |
| `createdAt` | `datetime` | Criacao. |
| `paidAt` | `datetime` | Pagamento. |
| `completedAt` | `datetime` | Conclusao. |

#### `WithdrawRequestDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `fromWalletName` | `string` | sim | Carteira de origem. |
| `toAddress` | `string` | sim | Endereco externo. |
| `amount` | `decimal` | sim | Valor do saque. |
| `description` | `string` | nao | Contexto. |
| `totpCode` | `string` | condicional | Fator adicional. |
| `passkeyAssertionResponseJSON` | `string` | condicional | Resposta WebAuthn. |
| `passkeyAssertionRequestJSON` | `string` | nao | Presente no DTO, mas o controller nao usa explicitamente. |
| `confirmationPassphrase` | `string` | condicional | Confirmacao adicional. |

#### `OnchainAddressRequestDTO`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `walletName` | `string` | sim |
| `regenerate` | `boolean` | nao |

#### `WalletNetworkAddressDTO`

| Campo | Tipo |
| --- | --- |
| `walletName` | `string` |
| `onchainAddress` | `string` |
| `lightningAddress` | `string` |
| `provider` | `string` |
| `externalWalletReference` | `string` |

#### `OnchainSendRequestDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `fromWalletName` | `string` | sim | Carteira de origem. |
| `toAddress` | `string` | sim | Destino on-chain. |
| `amount` | `decimal` | sim | Valor. |
| `description` | `string` | nao | Contexto. |
| `totpCode` | `string` | condicional | Fator adicional. |
| `passkeyAssertionResponseJSON` | `string` | condicional | Resposta WebAuthn. |
| `confirmationPassphrase` | `string` | condicional | Confirmacao adicional. |

#### `LightningInvoiceRequestDTO`

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `walletName` | `string` | sim |
| `amount` | `decimal` | sim |
| `memo` | `string` | nao |
| `expiresInSeconds` | `integer` | nao |

#### `LightningInvoiceResponseDTO`

| Campo | Tipo |
| --- | --- |
| `transferId` | `UUID` |
| `walletName` | `string` |
| `paymentRequest` | `string` |
| `paymentHash` | `string` |
| `lightningAddress` | `string` |
| `amountBtc` | `decimal` |
| `provider` | `string` |
| `expiresAt` | `datetime` |
| `status` | `string` |

#### `LightningPaymentRequestDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `fromWalletName` | `string` | sim | Carteira pagadora. |
| `paymentRequest` | `string` | sim | Invoice BOLT11. |
| `amount` | `decimal` | nao | Alguns provedores permitem deducao do proprio invoice. |
| `maxRoutingFeeBtc` | `decimal` | nao | Teto de fee. |
| `description` | `string` | nao | Contexto. |
| `totpCode` | `string` | condicional | Fator adicional. |
| `passkeyAssertionResponseJSON` | `string` | condicional | Resposta WebAuthn. |
| `confirmationPassphrase` | `string` | condicional | Confirmacao adicional. |

#### `ExternalTransferResponseDTO`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `id` | `UUID` | ID da transferencia. |
| `network` | `string` | Rede (`ONCHAIN`, `LIGHTNING`, etc.). |
| `transferType` | `string` | Tipo da transferencia. |
| `status` | `string` | Status atual. |
| `provider` | `string` | Provedor custodial. |
| `walletName` | `string` | Carteira relacionada. |
| `destination` | `string` | Destino. |
| `amountBtc` | `decimal` | Valor. |
| `networkFeeBtc` | `decimal` | Fee de rede. |
| `platformFeeBtc` | `decimal` | Fee da plataforma. |
| `totalDebitedBtc` | `decimal` | Total debitado. |
| `externalReference` | `string` | Referencia externa do provedor. |
| `expiresAt` | `datetime` | Expiracao. |
| `createdAt` | `datetime` | Criacao. |
| `updatedAt` | `datetime` | Atualizacao. |
| `context` | `string` | Contexto. |

#### `EconomyStatusData`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `withdrawalFeeSats` | `long` | Default `10000` se Redis nao tiver valor. |
| `withdrawalStatus` | `string` | Default `ENABLED` se Redis nao tiver valor. |

#### `BtcPriceData`

| Campo | Tipo |
| --- | --- |
| `btcUsd` | `decimal` |
| `btcBrl` | `decimal` |
| `usdBrl` | `decimal` |

#### `OnrampUrlsData`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `moonpay` | `string` | URL parametrizada. |
| `banxa` | `string` | URL parametrizada. |
| `bipa` | `string` | URL parametrizada. |
| `transferId` | `string` | UUID textual do registro interno. |
| `depositAddress` | `string` | Endereco rastreado dedicado. |
| `walletName` | `string` | Carteira alvo. |

### Schemas de mining

#### `MiningRigOfferDTO`

| Campo | Tipo |
| --- | --- |
| `id` | `long` |
| `rigCode` | `string` |
| `displayName` | `string` |
| `algorithm` | `string` |
| `hashUnit` | `string` |
| `availableHashrate` | `decimal` |
| `pricePerUnitDayBtc` | `decimal` |
| `projectedBtcYieldPerUnitDay` | `decimal` |
| `minRentalHours` | `integer` |
| `maxRentalHours` | `integer` |
| `provider` | `string` |

#### `MiningAllocationRequestDTO`

| Campo | Tipo | Obrigatorio | Observacao |
| --- | --- | --- | --- |
| `walletName` | `string` | sim | Carteira usada na cobranca. |
| `rigId` | `long` | sim | Rig escolhido. |
| `requestedHashrate` | `decimal` | sim | Hashrate desejado. |
| `budgetBtc` | `decimal` | sim | Orcamento. |
| `durationHours` | `integer` | sim | Duracao. |
| `payoutAddress` | `string` | sim | Endereco de payout. |
| `poolUrl` | `string` | nao | Pool opcional. |
| `workerName` | `string` | nao | Worker opcional. |
| `totpCode` | `string` | condicional | Fator adicional. |
| `passkeyAssertionResponseJSON` | `string` | condicional | Resposta WebAuthn. |
| `confirmationPassphrase` | `string` | condicional | Confirmacao adicional. |

#### `MiningAllocationResponseDTO`

| Campo | Tipo |
| --- | --- |
| `id` | `UUID` |
| `rigId` | `long` |
| `rigName` | `string` |
| `walletName` | `string` |
| `algorithm` | `string` |
| `allocatedHashrate` | `decimal` |
| `hashUnit` | `string` |
| `durationHours` | `integer` |
| `rentalCostBtc` | `decimal` |
| `projectedGrossYieldBtc` | `decimal` |
| `projectedNetYieldBtc` | `decimal` |
| `refundedAmountBtc` | `decimal` |
| `status` | `string` |
| `providerRentalReference` | `string` |
| `payoutAddress` | `string` |
| `poolUrl` | `string` |
| `workerName` | `string` |
| `startsAt` | `datetime` |
| `endsAt` | `datetime` |
| `settledAt` | `datetime` |

### Schemas diversos

#### `NotificationSendRequest`

`NotificationController` recebe um `Map<String,String>` com:

| Campo | Tipo | Obrigatorio |
| --- | --- | --- |
| `userId` | `string` | sim |
| `title` | `string` | sim |
| `body` | `string` | sim |

#### `VoucherRequestDataResponse`

| Campo | Tipo |
| --- | --- |
| `depositAddress` | `string` |
| `amountSats` | `long` |
| `pendingVoucherId` | `string` |

#### `PowChallengeData`

| Campo | Tipo |
| --- | --- |
| `challenge` | `string` |

#### `SovereigntyStatusResponse`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `hardwareAttestation` | `object` | Ver abaixo. |
| `networkConsensus` | `object` | Ver abaixo. |
| `ledgerIntegrity` | `object` | Ver abaixo. |
| `memoryProtection` | `object` | Ver abaixo. |
| `serverUptimeSeconds` | `long` | Uptime desde o start da JVM. |
| `serverTimestamp` | `string` | `Instant` atual. |

`hardwareAttestation`:

| Campo | Tipo |
| --- | --- |
| `status` | `string` |
| `chip` | `string` |
| `lastValidatedSecondsAgo` | `long` |
| `totalChecks` | `long` |
| `quoteHash` | `string` |
| `tmeEnabled` | `boolean` |
| `coldBootRisk` | `string` |

`networkConsensus`:

| Campo | Tipo |
| --- | --- |
| `status` | `string` |
| `activeNodes` | `integer` |
| `failStopMode` | `boolean` |
| `transactionsAccepted` | `long` |
| `requiredNodes` | `integer` |
| `totalNodes` | `integer` |
| `jurisdictions` | `string[]` |
| `consensusAlgorithm` | `string` |

`ledgerIntegrity`:

| Campo | Tipo |
| --- | --- |
| `status` | `string` |
| `lastRootHash` | `string` |
| `computedAt` | `string` |
| `ledgerCount` | `long` |

`memoryProtection`:

| Campo | Tipo |
| --- | --- |
| `status` | `string` |
| `mechanism` | `string` |
| `shardLocation` | `string` |
| `diskPersistence` | `boolean` |

#### `TelemetrySnapshot`

| Campo | Tipo | Observacao |
| --- | --- | --- |
| `snapshotAt` | `string` | `Instant` textual. |
| `storage` | `string` | Sempre `"RAM_ONLY — no disk persistence"`. |
| `counters` | `object` | Ver abaixo. |
| `recentEvents` | `string[]` | Ring buffer em memoria. |

`counters`:

| Campo | Tipo |
| --- | --- |
| `quorumFailures` | `long` |
| `stallEvents` | `long` |
| `heartbeatFailures` | `long` |
| `tpmChecksTotal` | `long` |
| `tpmMismatches` | `long` |
| `suicideTriggers` | `long` |
| `transactionsProposed` | `long` |
| `transactionsAccepted` | `long` |

## Inconsistencias encontradas durante a analise

1. Comentarios de "rota publica" nao batem com a seguranca real.
   `Security.java` libera explicitamente apenas `/auth/**` previstos, `/voucher/**` e Swagger. Portanto `/ledger/**`, `/transactions/**`, `/api/**`, `/audit/**`, `/v1/audit/**`, `/wallet/**`, `/mining/**`, `/notifications/**` e `/sovereignty/**` exigem JWT hoje.

2. O contrato de erro nao e uniforme.
   A aplicacao usa ao mesmo tempo `ApiResponse`, `ResponseError`, `Map` bruto e erros padrao do Spring.

3. `RestResponseErrors` convive com `GlobalExceptionHandler`.
   Isso faz alguns erros de auth, wallet e replay terem envelope ambiguo em runtime.
