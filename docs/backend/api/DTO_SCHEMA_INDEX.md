# DTO Schema Index

Índice auxiliar dos DTOs usados pelos endpoints documentados.

Este arquivo não é a documentação operacional de API. Para entender um endpoint, consulte primeiro o documento do domínio em [`docs/backend/api/`](README.md), especialmente [`KFE.md`](KFE.md) para fluxos financeiros.

Fonte técnica: DTOs Java em `backend/kerosene/src/main/java/source/**`, controllers ativos, `EndpointPolicyRegistry`, configuração de segurança e anotações `@PreAuthorize`.

Regra KFE-only: DTOs financeiros legados removidos não devem aparecer neste índice como schemas ativos.

## `AccountActivationStatusDTO`

Fonte: [AccountActivationStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountActivationStatusDTO.java)

- `boolean activated`
- `boolean canReceiveInbound`
- `boolean requiresActivationDeposit`
- `BigDecimal requiredAmountBtc`
- `String paymentLinkId`
- `String depositAddress`
- `String paymentStatus`
- `String warningMessage`
- `LocalDateTime activatedAt`

## `AccountSecurityProfileDTO`

Fonte: [AccountSecurityProfileDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountSecurityProfileDTO.java)

- `AccountSecurityType accountSecurity`
- `Integer shamirTotalShares`
- `Integer shamirThreshold`
- `Integer multisigThreshold`
- `boolean passkeyAvailable`
- `boolean passkeyEnabledForTransactions`
- `AppPinStatusDTO appPin`
- `List<String> requiredFactors`
- `PasskeyInventoryDTO passkeys`

## `AccountSecurityStatusDTO`

Fonte: [AccountSecurityStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountSecurityStatusDTO.java)

- `boolean passwordConfigured`
- `boolean passkeyRegistered`
- `boolean totpEnabled`
- `int backupCodesRemaining`
- `boolean unprotected`
- `String warningMessage`
- `boolean accountActivated`
- `boolean inboundEnabled`
- `PasskeyInventoryDTO passkeys`

## `AccountSecurityUpdateRequestDTO`

Fonte: [AccountSecurityUpdateRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AccountSecurityUpdateRequestDTO.java)

- `accountSecurity: AccountSecurityType`
- `shamirTotalShares: Integer`
- `shamirThreshold: Integer`
- `multisigThreshold: Integer`

## `AdminAccessAttemptDTO`

Fonte: [AdminAccessAttemptDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminAccessAttemptDTO.java)

- `UUID attemptId`
- `String status`
- `String deviceId`
- `String deviceName`
- `String browser`
- `String userAgent`
- `String ipFingerprint`
- `LocalDateTime requestedAt`
- `LocalDateTime expiresAt`

## `AdminAccessDecisionRequestDTO`

Fonte: [AdminAccessDecisionRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminAccessDecisionRequestDTO.java)

- `decision: String`

## `AdminAuthenticatedDeviceDTO`

Fonte: [AdminAuthenticatedDeviceDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminAuthenticatedDeviceDTO.java)

- `String deviceId`
- `String deviceName`
- `String browser`
- `String userAgent`
- `String status`
- `LocalDateTime firstAccessAt`
- `LocalDateTime lastAccessAt`

## `AdminKeyCreateRequestDTO`

Fonte: [AdminKeyCreateRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminKeyCreateRequestDTO.java)

- `keyMaterialHash: String`
- `deviceInstallId: String`

## `AdminKeyStatusDTO`

Fonte: [AdminKeyStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminKeyStatusDTO.java)

- `boolean configured`
- `String status`
- `String fingerprint`
- `LocalDateTime createdAt`
- `LocalDateTime revokedAt`

## `AdminLoginRequestDTO`

Fonte: [AdminLoginRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminLoginRequestDTO.java)

- `username: String`
- `adminKeyProof: String`
- `deviceId: String`
- `deviceName: String`
- `browser: String`
- `userAgent: String`
- `platform: String`

## `AdminLoginResponseDTO`

Fonte: [AdminLoginResponseDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AdminLoginResponseDTO.java)

- `String status`
- `boolean requiresMobileApproval`
- `UUID attemptId`
- `LocalDateTime expiresAt`
- `String token`
- `String message`

## `AppPinStatusDTO`

Fonte: [AppPinStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/AppPinStatusDTO.java)

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

## `BackupCodesStatusDTO`

Fonte: [BackupCodesStatusDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/BackupCodesStatusDTO.java)

- `boolean enabled`
- `int remainingCodes`
- `List<String> newlyGeneratedCodes`

## `ConfigureAppPinRequestDTO`

Fonte: [ConfigureAppPinRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/ConfigureAppPinRequestDTO.java)

- `enabled: Boolean`
- `pin: String`
- `currentPin: String`
- `totpCode: String`

## `DeviceKeyChallengeResponse`

Fonte: [DeviceKeyChallengeResponse.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyChallengeResponse.java)

- `String challengeId`
- `String challenge`
- `long expiresInSeconds`
- `String onionServiceId`
- `String algorithm`
- `String canonicalization`

## `DeviceKeyDeviceDTO`

Fonte: [DeviceKeyDeviceDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyDeviceDTO.java)

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

## `DeviceKeyRegistrationRequest`

Fonte: [DeviceKeyRegistrationRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyRegistrationRequest.java)

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

## `DeviceKeyVerifyRequest`

Fonte: [DeviceKeyVerifyRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/devicekey/DeviceKeyVerifyRequest.java)

- `username: String`
- `credentialId: String`
- `deviceInstallId: String`
- `signedPayload: String`
- `signature: String`

## `DeviceTokenRegisterRequest`

Fonte: [DeviceTokenRegisterRequest.java](../../../backend/kerosene/src/main/java/source/notification/dto/DeviceTokenRegisterRequest.java)

- `String platform`
- `String token`
- `String deviceId`
- `String appVersion`

## `DeviceTokenResponse`

Fonte: [DeviceTokenResponse.java](../../../backend/kerosene/src/main/java/source/notification/dto/DeviceTokenResponse.java)

- `Long id`
- `String platform`
- `String tokenRef`
- `String deviceRef`
- `String appVersion`
- `LocalDateTime createdAt`
- `LocalDateTime lastSeenAt`
- `LocalDateTime revokedAt`
- `boolean active`

## `EmergencyRecoveryFinishRequest`

Fonte: [EmergencyRecoveryFinishRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryFinishRequest.java)

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

## `EmergencyRecoveryFinishResponse`

Fonte: [EmergencyRecoveryFinishResponse.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryFinishResponse.java)

- `username: String`
- `newBackupCodes: List<String>`

## `EmergencyRecoveryStartRequest`

Fonte: [EmergencyRecoveryStartRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryStartRequest.java)

- `username: String`
- `recoveryCodes: List<String>`
- `challenge: String`
- `nonce: String`

## `EmergencyRecoveryStartResponse`

Fonte: [EmergencyRecoveryStartResponse.java](../../../backend/kerosene/src/main/java/source/auth/dto/EmergencyRecoveryStartResponse.java)

- `recoverySessionId: String`
- `otpUri: String`
- `passkeyChallenge: String`
- `expiresInSeconds: long`
- `requiredRecoveryCodes: int`

## `KfeAddressResponse`

Fonte: [KfeAddressResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAddressResponse.java)

- `UUID id`
- `UUID walletId`
- `String address`
- `KfeWalletAddressRole role`
- `KfeWalletAddressStatus status`
- `String derivationPath`
- `Integer derivationIndex`
- `String providerReference`
- `LocalDateTime createdAt`
- `LocalDateTime retiredAt`

## `KfeAuditEventResponse`

Fonte: [KfeAuditEventResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAuditEventResponse.java)

- `Long sequenceNumber`
- `UUID id`
- `UUID transactionId`
- `UUID walletId`
- `String eventType`
- `String fromStatus`
- `String toStatus`
- `String payloadHash`
- `String previousHash`
- `String eventHash`
- `LocalDateTime createdAt`

## `KfeAuditLatestResponse`

Fonte: [KfeAuditLatestResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAuditLatestResponse.java)

- `KfeAuditEventResponse latestEvent`
- `KfeAuditRootResponse root`

## `KfeAuditRootResponse`

Fonte: [KfeAuditRootResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeAuditRootResponse.java)

- `String merkleRoot`
- `long eventCount`
- `Long fromSequence`
- `Long toSequence`
- `LocalDateTime generatedAt`

## `KfeColdWalletPsbtRequest`

Fonte: [KfeColdWalletPsbtRequest.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeColdWalletPsbtRequest.java)

- `@NotBlank @Size(max = 128) String destinationAddress`
- `@Min(546) long amountSats`
- `@Min(1) Integer confirmationTarget`
- `@Min(1) Long feeRateSatsPerVbyte`
- `@Valid List<Input> inputs`

## `KfeColdWalletPsbtResponse`

Fonte: [KfeColdWalletPsbtResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeColdWalletPsbtResponse.java)

- `String psbt`
- `String psbtHash`
- `long feeSats`
- `long amountSats`
- `String destinationAddress`
- `List<KfeColdWalletPsbtRequest.Input> inputs`

## `KfeCreateWalletRequest`

Fonte: [KfeCreateWalletRequest.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeCreateWalletRequest.java)

- `@NotNull KfeWalletKind kind`
- `KfeWalletName name`
- `@Size(max = 96) String label`
- `String xpub`
- `String descriptor`
- `String fingerprint`
- `String derivationPath`
- `String initialAddress`
- `String initialAddressDerivationPath`
- `Integer initialAddressDerivationIndex`
- `String initialAddressProviderReference`
- `Boolean issueInitialAddress`

## `KfeDashboardResponse`

Fonte: [KfeDashboardResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeDashboardResponse.java)

- `List<KfeDashboardWallet> wallets`
- `List<KfeStatementItem> recentStatement`
- `long totalSpendableSats`
- `long totalObservedSats`
- `long totalVisibleSats`

## `KfeReceivingCapabilitiesResponse`

Fonte: [KfeReceivingCapabilitiesResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeReceivingCapabilitiesResponse.java)

- `boolean canReceiveInternal`
- `boolean canReceiveLightning`
- `boolean canReceiveOnchain`
- `String preferredRail`
- `List<String> missingRequirements`
- `String receiverDisplayName`
- `UUID internalWalletId`
- `List<String> availableRails`
- `Limits limits`

## `KfeSubmitTransactionRequest`

Fonte: [KfeSubmitTransactionRequest.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeSubmitTransactionRequest.java)

- `@NotBlank String idempotencyKey`
- `@NotNull KfeRail rail`
- `@NotNull KfeDirection direction`
- `UUID sourceWalletId`
- `UUID destinationWalletId`
- `@Min(1) long amountSats`
- `@Min(0) long networkFeeSats`
- `String externalReference`
- `String memo`
- `String totpCode`
- `String passkeyAssertionJson`
- `String confirmationPassphrase`

## `KfeTransactionResponse`

Fonte: [KfeTransactionResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeTransactionResponse.java)

- `UUID id`
- `KfeTransactionStatus status`
- `KfeRail rail`
- `KfeDirection direction`
- `UUID sourceWalletId`
- `UUID destinationWalletId`
- `long grossAmountSats`
- `long receiverAmountSats`
- `long networkFeeSats`
- `long keroseneFeeSats`
- `long totalDebitSats`
- `String quorumProposalHash`
- `int quorumAckCount`
- `String providerReference`
- `String blockchainTxid`
- `String failureCode`
- `String failureMessage`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`

## `KfeUtxoResponse`

Fonte: [KfeUtxoResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeUtxoResponse.java)

- `String txid`
- `int vout`
- `long valueSats`
- `String scriptPubKey`
- `String address`

## `KfeWalletNameOption`

Fonte: [KfeWalletNameOption.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeWalletNameOption.java)

- `KfeWalletName name`
- `String label`

## `KfeWalletResponse`

Fonte: [KfeWalletResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeWalletResponse.java)

- `UUID id`
- `KfeWalletKind kind`
- `KfeWalletStatus status`
- `String label`
- `String walletName`
- `String walletTypeDescription`
- `String asset`
- `boolean spendable`
- `boolean xpubConfigured`
- `boolean mpcKeyConfigured`
- `String activeAddress`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`

## `MiningAllocationRequestDTO`

Fonte: [MiningAllocationRequestDTO.java](../../../backend/kerosene/src/main/java/source/mining/dto/MiningAllocationRequestDTO.java)

- `String walletName`
- `Long rigId`
- `BigDecimal requestedHashrate`
- `BigDecimal budgetBtc`
- `Integer durationHours`
- `String payoutAddress`
- `String poolUrl`
- `String workerName`
- `String totpCode`
- `String passkeyAssertionResponseJSON`
- `String confirmationPassphrase`

## `MiningAllocationResponseDTO`

Fonte: [MiningAllocationResponseDTO.java](../../../backend/kerosene/src/main/java/source/mining/dto/MiningAllocationResponseDTO.java)

- `UUID id`
- `Long rigId`
- `String rigName`
- `String walletName`
- `String algorithm`
- `BigDecimal allocatedHashrate`
- `String hashUnit`
- `Integer durationHours`
- `BigDecimal rentalCostBtc`
- `BigDecimal projectedGrossYieldBtc`
- `BigDecimal projectedNetYieldBtc`
- `BigDecimal refundedAmountBtc`
- `String status`
- `String providerRentalReference`
- `String payoutAddress`
- `String poolUrl`
- `String workerName`
- `LocalDateTime startsAt`
- `LocalDateTime endsAt`
- `LocalDateTime settledAt`

## `MiningRigOfferDTO`

Fonte: [MiningRigOfferDTO.java](../../../backend/kerosene/src/main/java/source/mining/dto/MiningRigOfferDTO.java)

- `Long id`
- `String rigCode`
- `String displayName`
- `String algorithm`
- `String hashUnit`
- `BigDecimal availableHashrate`
- `BigDecimal pricePerUnitDayBtc`
- `BigDecimal projectedBtcYieldPerUnitDay`
- `Integer minRentalHours`
- `Integer maxRentalHours`
- `String provider`

## `PasskeyInventoryDTO`

Fonte: [PasskeyInventoryDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/PasskeyInventoryDTO.java)

- `boolean passkeyRegistered`
- `boolean compatibleForCurrentLogin`
- `boolean legacyCredentialsPresent`
- `String currentRelyingPartyId`
- `String currentHost`
- `List<PasskeyDeviceDTO> devices`

## `PasskeyRegistrationRequest`

Fonte: [PasskeyRegistrationRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/passkey/PasskeyRegistrationRequest.java)

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

## `PasskeyVerifyRequest`

Fonte: [PasskeyVerifyRequest.java](../../../backend/kerosene/src/main/java/source/auth/dto/passkey/PasskeyVerifyRequest.java)

- `username: String`
- `signature: String`
- `authData: String`
- `clientDataJSON: String`
- `credentialId: String`

## `SignupResponseDTO`

Fonte: [SignupResponseDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/SignupResponseDTO.java)

- `sessionId: String`
- `otpUri: String`
- `backupCodes: List<String>`
- `totpOptional: boolean`

## `SignupTotpVerifyRequestDTO`

Fonte: [SignupTotpVerifyRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/SignupTotpVerifyRequestDTO.java)

- `sessionId: String`
- `totpCode: String`

## `TotpSetupResponseDTO`

Fonte: [TotpSetupResponseDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/TotpSetupResponseDTO.java)

- `String otpUri`
- `String secret`

## `KfeReserveOverviewResponse`

Fonte: [KfeReserveOverviewResponse.java](../../../backend/kerosene/src/main/java/source/kfe/dto/KfeReserveOverviewResponse.java)

- `BigDecimal totalOnchainBtc`
- `BigDecimal lightningNodeBtc`
- `BigDecimal inboundLiquidityBtc`
- `BigDecimal outboundLiquidityBtc`
- `BigDecimal reservedOnchainBtc`
- `BigDecimal reservedLightningBtc`
- `BigDecimal availableOnchainBtc`
- `BigDecimal availableLightningBtc`
- `boolean lightningSendsAllowed`
- `String liquidityState`

## `UserDTO`

Fonte: [UserDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/UserDTO.java)

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

## `VerifyAppPinRequestDTO`

Fonte: [VerifyAppPinRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/VerifyAppPinRequestDTO.java)

- `pin: String`
