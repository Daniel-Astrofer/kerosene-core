# DTO Schema Index

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


Este indice lista DTOs usados pelos endpoints documentados. Ele nao substitui a fonte Java; serve para navegacao rapida.

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

## `BroadcastTransactionDTO`

Fonte: [BroadcastTransactionDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/BroadcastTransactionDTO.java)

- `rawTxHex: String`
- `toAddress: String`
- `amount: java.math.BigDecimal`
- `message: String`

## `CancelPaymentLinkRequest`

Fonte: [CancelPaymentLinkRequest.java](../../../backend/kerosene/src/main/java/source/transactions/dto/CancelPaymentLinkRequest.java)

- `reason: String`

## `ConfigureAppPinRequestDTO`

Fonte: [ConfigureAppPinRequestDTO.java](../../../backend/kerosene/src/main/java/source/auth/dto/ConfigureAppPinRequestDTO.java)

- `enabled: Boolean`
- `pin: String`
- `currentPin: String`
- `totpCode: String`

## `ConfirmPaymentRequest`

Fonte: [ConfirmPaymentRequest.java](../../../backend/kerosene/src/main/java/source/transactions/dto/ConfirmPaymentRequest.java)

- `idempotencyKey: String`
- `txid: String`
- `fromAddress: String`

## `CreatePaymentLinkRequest`

Fonte: [CreatePaymentLinkRequest.java](../../../backend/kerosene/src/main/java/source/transactions/dto/CreatePaymentLinkRequest.java)

- `amount: BigDecimal`
- `description: String`
- `expiresInMinutes: Integer`
- `visibility: String`
- `confirmationMode: String`
- `amountLocked: Boolean`
- `referenceLabel: String`
- `metadata: Map<String, String>`

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

## `EstimatedFeeDTO`

Fonte: [EstimatedFeeDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/EstimatedFeeDTO.java)

- `fastSatoshisPerByte: Long`
- `standardSatoshisPerByte: Long`
- `slowSatoshisPerByte: Long`
- `estimatedFastBtc: BigDecimal`
- `estimatedStandardBtc: BigDecimal`
- `estimatedSlowBtc: BigDecimal`
- `amountReceived: BigDecimal`
- `totalToSend: BigDecimal`

## `ExternalTransferResponseDTO`

Fonte: [ExternalTransferResponseDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/ExternalTransferResponseDTO.java)

- `UUID id`
- `String network`
- `String transferType`
- `String status`
- `String provider`
- `String walletName`
- `String destination`
- `String invoiceId`
- `String blockchainTxid`
- `String paymentHash`
- `String invoiceData`
- `BigDecimal expectedAmountBtc`
- `BigDecimal amountBtc`
- `BigDecimal networkFeeBtc`
- `BigDecimal platformFeeBtc`
- `BigDecimal totalDebitedBtc`
- `String externalReference`
- `Integer confirmations`
- `LocalDateTime expiresAt`
- `LocalDateTime detectedAt`
- `LocalDateTime settledAt`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`
- `String context`

## `InternalPaymentRequestDTO`

Fonte: [InternalPaymentRequestDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/InternalPaymentRequestDTO.java)

- `id: String`
- `requesterUserId: Long`
- `receiverWalletId: Long`
- `receiverWalletName: String`
- `destinationHash: String`
- `amount: BigDecimal`
- `status: String`
- `expiresAt: LocalDateTime`
- `createdAt: LocalDateTime`
- `paidAt: LocalDateTime`

## `InternalTransactionResponseDTO`

Fonte: [InternalTransactionResponseDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/InternalTransactionResponseDTO.java)

- `String txid`
- `String status`
- `BigDecimal amount`
- `String sender`
- `String receiver`
- `String context`

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

## `LedgerDTO`

Fonte: [LedgerDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/LedgerDTO.java)

- `id: Integer`
- `walletId: Long`
- `walletName: String`
- `balance: BigDecimal`
- `nonce: Integer`
- `lastHash: String`
- `context: String`
- `amount: BigDecimal`

## `LightningInvoiceRequestDTO`

Fonte: [LightningInvoiceRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/LightningInvoiceRequestDTO.java)

- `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters") String idempotencyKey`
- `@NotBlank(message = "walletName is required") String walletName`
- `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places") BigDecimal amount`
- `String memo`
- `Integer expiresInSeconds`

## `LightningInvoiceResponseDTO`

Fonte: [LightningInvoiceResponseDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/LightningInvoiceResponseDTO.java)

- `UUID transferId`
- `String walletName`
- `String paymentRequest`
- `String paymentHash`
- `String lightningAddress`
- `BigDecimal amountBtc`
- `String provider`
- `LocalDateTime expiresAt`
- `String status`

## `LightningPaymentRequestDTO`

Fonte: [LightningPaymentRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/LightningPaymentRequestDTO.java)

- `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters") String idempotencyKey`
- `@NotBlank(message = "fromWalletName is required") String fromWalletName`
- `@NotBlank(message = "paymentRequest is required") String paymentRequest`
- `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places") BigDecimal amount`
- `BigDecimal maxRoutingFeeBtc`
- `String description`
- `String totpCode`
- `String passkeyAssertionResponseJSON`
- `String confirmationPassphrase`

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

## `OnchainAddressAllocationDTO`

Fonte: [OnchainAddressAllocationDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/OnchainAddressAllocationDTO.java)

- `String walletName`
- `String onchainAddress`
- `BigDecimal expectedAmountBtc`
- `String network`
- `String provider`
- `String externalWalletReference`
- `String walletMode`
- `UUID transferId`
- `String transferStatus`
- `Integer confirmations`
- `Integer requiredConfirmations`
- `String blockchainTxid`

## `OnchainAddressRequestDTO`

Fonte: [OnchainAddressRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/OnchainAddressRequestDTO.java)

- `String walletName`
- `BigDecimal expectedAmountBtc`

## `OnchainSendRequestDTO`

Fonte: [OnchainSendRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/OnchainSendRequestDTO.java)

- `@NotBlank(message = "idempotencyKey is required") @Size(max = 96, message = "idempotencyKey must have at most 96 characters") String idempotencyKey`
- `@NotBlank(message = "fromWalletName is required") String fromWalletName`
- `@NotBlank(message = "toAddress is required") String toAddress`
- `@NotNull(message = "amount is required") @DecimalMin(value = "0.00000001", message = "amount must be greater than zero") @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount") @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places") BigDecimal amount`
- `String description`
- `String totpCode`
- `String passkeyAssertionResponseJSON`
- `String confirmationPassphrase`

## `OperationalReserveProofResponseDTO`

Fonte: [OperationalReserveProofResponseDTO.java](../../../backend/kerosene/src/main/java/source/kfe/dto/OperationalReserveProofResponseDTO.java)

- `Instant generatedAt`
- `String status`
- `boolean solvent`
- `boolean providersHealthy`
- `Assets assets`
- `Liabilities liabilities`
- `ChainState chainState`
- `MerkleProof merkleProof`
- `List<ProviderHealth> providers`
- `String snapshotHash`
- `String panicReason`

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

## `PaymentConfirmRequest`

Fonte: [PaymentConfirmRequest.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentConfirmRequest.java)

- `@NotBlank @Size(max = 128) String idempotencyKey`
- `@Size(max = 512) String userConfirmationToken`
- `@NotNull Long acceptedTotalDebitSats`
- `@NotNull Long acceptedReceiverAmountSats`

## `PaymentLinkDTO`

Fonte: [PaymentLinkDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/PaymentLinkDTO.java)

- `id: String`
- `userId: Long`
- `sessionId: String`
- `amountBtc: BigDecimal`
- `grossAmountBtc: BigDecimal`
- `depositFeeBtc: BigDecimal`
- `netAmountBtc: BigDecimal`
- `description: String`
- `depositAddress: String`
- `visibility: String`
- `confirmationMode: String`
- `amountLocked: Boolean`
- `referenceLabel: String`
- `metadata: Map<String, String>`
- `status: String`
- `txid: String`
- `expiresAt: LocalDateTime`
- `createdAt: LocalDateTime`
- `paidAt: LocalDateTime`
- `completedAt: LocalDateTime`
- `cancelledAt: LocalDateTime`
- `cancelReason: String`
- `paymentRail: String`
- `paymentIntentStatus: String`

## `PaymentQuoteRequest`

Fonte: [PaymentQuoteRequest.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentQuoteRequest.java)

- `@NotNull PaymentEnums.PaymentRail rail`
- `@NotNull PaymentEnums.FeeMode feeMode`
- `@NotBlank @Size(max = 40) String amountFiat`
- `@NotBlank @Size(max = 8) String fiatCurrency`
- `@NotBlank @Size(max = 16) String asset`
- `@Size(max = 255) String receiverIdentifier`
- `@Size(max = 2048) String externalDestination`
- `PaymentEnums.OnchainSpeed speed`

## `PaymentQuoteResponse`

Fonte: [PaymentQuoteResponse.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentQuoteResponse.java)

- `UUID paymentIntentId`
- `Instant quoteExpiresAt`
- `PaymentEnums.PaymentRail rail`
- `PaymentEnums.FeeMode feeMode`
- `String receiverDisplayName`
- `String receiverAmountFiat`
- `long receiverAmountSats`
- `String totalDebitFiat`
- `long totalDebitSats`
- `String networkFeeFiat`
- `long networkFeeSats`
- `String keroseneFeeFiat`
- `long keroseneFeeSats`
- `List<String> warnings`
- `boolean requiresConfirmation`

## `PaymentRequestPublicDTO`

Fonte: [PaymentRequestPublicDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/PaymentRequestPublicDTO.java)

- `id: String`
- `amount: BigDecimal`
- `status: String`
- `expiresAt: LocalDateTime`
- `destinationHash: String`
- `locked: boolean`

## `PaymentStatusResponse`

Fonte: [PaymentStatusResponse.java](../../../backend/kerosene/src/main/java/source/payments/dto/PaymentStatusResponse.java)

- `UUID paymentIntentId`
- `PaymentEnums.PaymentIntentStatus status`
- `PaymentEnums.PaymentRail rail`
- `PaymentEnums.FeeMode feeMode`
- `String receiverDisplayName`
- `long receiverAmountSats`
- `long totalDebitSats`
- `long networkFeeSats`
- `long keroseneFeeSats`
- `Instant quoteExpiresAt`
- `String failureCode`
- `String failureMessage`
- `List<String> warnings`

## `ReceivingCapabilitiesResponse`

Fonte: [ReceivingCapabilitiesResponse.java](../../../backend/kerosene/src/main/java/source/payments/dto/ReceivingCapabilitiesResponse.java)

- `boolean canReceiveInternal`
- `boolean canReceiveLightning`
- `boolean canReceiveOnchain`
- `PaymentEnums.PaymentRail preferredRail`
- `List<String> missingRequirements`
- `String receiverDisplayName`
- `List<PaymentEnums.PaymentRail> availableRails`
- `Limits limits`

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

## `TransactionDTO`

Fonte: [TransactionDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/TransactionDTO.java)

- `sender: String`
- `receiver: String`
- `amount: BigDecimal`
- `context: String`
- `idempotencyKey: String`
- `requestTimestamp: Long`
- `passkeyAssertionJson: String`
- `confirmationPassphrase: String`
- `totpCode: String`

## `TransactionRequestDTO`

Fonte: [TransactionRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/TransactionRequestDTO.java)

- `fromAddress: String`
- `toAddress: String`
- `amount: BigDecimal`
- `feeSatoshis: Long`

## `TransactionResponseDTO`

Fonte: [TransactionResponseDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/TransactionResponseDTO.java)

- `txid: String`
- `status: String`
- `feeSatoshis: Long`
- `amountReceived: BigDecimal`
- `sender: String`
- `receiver: String`
- `context: String`

## `TreasuryAuditConfigRequestDTO`

Fonte: [TreasuryAuditConfigRequestDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/TreasuryAuditConfigRequestDTO.java)

- `BigDecimal maxWithdrawLimit`
- `String auditXpub`

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

## `UnsignedTransactionDTO`

Fonte: [UnsignedTransactionDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/UnsignedTransactionDTO.java)

- `rawTxHex: String`
- `txId: String`
- `inputs: List<TransactionInput>`
- `outputs: List<TransactionOutput>`
- `totalAmount: BigDecimal`
- `fee: Long`
- `fromAddress: String`
- `toAddress: String`
- `txid: String`
- `vout: Integer`
- `value: BigDecimal`
- `scriptPubKey: String`
- `address: String`
- `value: BigDecimal`

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

## `WalletNetworkAddressDTO`

Fonte: [WalletNetworkAddressDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/WalletNetworkAddressDTO.java)

- `String walletName`
- `String onchainAddress`
- `String lightningAddress`
- `String network`
- `String provider`
- `String externalWalletReference`
- `String walletMode`
- `boolean lightningEnabled`
- `String lightningUnavailableReason`

## `WalletRequestDTO`

Fonte: [WalletRequestDTO.java](../../../backend/kerosene/src/main/java/source/wallet/dto/WalletRequestDTO.java)

- `@NotBlank(message = "A passphrase é obrigatória") char[] passphrase`
- `@NotBlank(message = "O nome da carteira é obrigatório") @Size(min = 3, max = 50, message = "O nome deve ter entre 3 e 50 caracteres") String name`
- `String xpub`
- `String walletMode`

## `WalletResponseDTO`

Fonte: [WalletResponseDTO.java](../../../backend/kerosene/src/main/java/source/wallet/dto/WalletResponseDTO.java)

- `Long id`
- `String name`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`
- `Boolean isActive`
- `String totpUri`
- `String depositAddress`
- `String lightningAddress`
- `String walletMode`
- `Boolean xpubConfigured`
- `String cardType`
- `String cardHolderName`
- `String cardMaskedNumber`
- `String cardNumberSuffix`
- `Integer cardSequence`
- `String cardRotationStatus`
- `LocalDateTime cardIssuedAt`
- `LocalDateTime cardExpiresAt`
- `LocalDateTime cardNextRotationAt`
- `LocalDateTime cardLastRotatedAt`
- `String previousCardNumberSuffix`
- `LocalDateTime previousCardExpiresAt`
- `BigDecimal withdrawalFeeRate`
- `BigDecimal depositFeeRate`

## `WalletUpdateDTO`

Fonte: [WalletUpdateDTO.java](../../../backend/kerosene/src/main/java/source/wallet/dto/WalletUpdateDTO.java)

- `@NotBlank(message = "A passphrase é obrigatória para autorizar a modificação") char[] passphrase`
- `@NotBlank(message = "O nome atual da carteira é obrigatório") String name`
- `@Size(min = 3, max = 50, message = "O novo nome deve ter entre 3 e 50 caracteres") String newName`
- `String newXpub`
- `String newWalletMode`

## `WithdrawRequestDTO`

Fonte: [WithdrawRequestDTO.java](../../../backend/kerosene/src/main/java/source/transactions/dto/WithdrawRequestDTO.java)

- `idempotencyKey: String`
- `fromWalletName: String`
- `toAddress: String`
- `amount: BigDecimal`
- `description: String`
- `totpCode: String`
- `passkeyAssertionResponseJSON: String`
- `passkeyAssertionRequestJSON: String`
- `confirmationPassphrase: String`
