package com.kerosene.auth.application.service.identityaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyVerificationProjection;
import com.kerosene.auth.application.service.crypto.contracts.Hasher;
import com.kerosene.auth.application.service.devicebinding.DeviceCredentialReplayGuard;
import com.kerosene.auth.application.service.devicekey.DeviceKeyService;
import com.kerosene.auth.application.service.devicekey.DeviceKeyReplayException;
import com.kerosene.auth.application.service.passkey.PasskeyInventoryService;
import com.kerosene.auth.application.service.passkey.PasskeyService;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPVerifier;
import com.kerosene.auth.dto.devicekey.DeviceKeyVerifyRequest;
import com.kerosene.auth.model.entity.DeviceKeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.auth.model.enums.AccountSecurityType;
import com.kerosene.common.exception.ErrorCodes;
import com.kerosene.common.infra.logging.LogDomain;
import com.kerosene.platform.util.CryptoUtils;

import java.time.LocalDateTime;
import java.util.Map;

/** Authorizes sensitive operations against account mode, transaction scope, and enrolled factors. */
@Service
public class TransactionalAuthenticationService implements TransactionalAuthenticationPort {

    /** Operational and security diagnostics for step-up authorization. */
    private static final Logger log = LoggerFactory.getLogger(TransactionalAuthenticationService.class);

    /** Verifies WebAuthn assertions and consumes username-scoped challenges. */
    private final PasskeyService passkeyService;
    /** Supplies compatibility checks, challenge recovery payloads, and replay guidance. */
    private final PasskeyInventoryService passkeyInventoryService;
    /** Looks up WebAuthn credentials and advances monotonic signature counters. */
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    /** Looks up device-key credentials and atomically advances their counters. */
    private final DeviceKeyCredentialRepository deviceKeyCredentialRepository;
    /** Validates device-key Ed25519 proof and challenge state. */
    private final DeviceKeyService deviceKeyService;
    /** Tracks replay conflicts and applies temporary locks to credentials. */
    private final DeviceCredentialReplayGuard deviceCredentialReplayGuard;
    /** Verifies TOTP factors when required by scope or account mode. */
    private final TOTPVerifier totpVerifier;
    /** Argon2-qualified verifier for transaction passphrase confirmation. */
    private final Hasher hasher;
    /** Resolves account data when the request carries only a principal ID. */
    private final UserServiceContract userService;
    /** Optional platform co-signer used by advanced account modes. */
    private final PlatformTransactionSignerPort platformTransactionSigner;
    /** Parses client assertion JSON and device-key assertion discriminators. */
    private final ObjectMapper objectMapper;

    /**
     * Creates the authorization service from passkey, device-key, factor, persistence, and signing boundaries.
     * @param passkeyService WebAuthn assertion verifier
     * @param passkeyInventoryService compatibility/guidance service
     * @param passkeyCredentialRepository WebAuthn lookup and counter updates
     * @param deviceKeyCredentialRepository device-key lookup and counter updates
     * @param deviceKeyService device-key protocol verifier
     * @param deviceCredentialReplayGuard replay counter failure policy
     * @param totpVerifier TOTP verifier
     * @param hasher Argon2-qualified passphrase verifier
     * @param userService account lookup/persistence service
     * @param platformTransactionSigner platform co-signing port
     * @param objectMapper JSON parser for assertion payloads
     */
    public TransactionalAuthenticationService(
            PasskeyService passkeyService,
            PasskeyInventoryService passkeyInventoryService,
            PasskeyCredentialRepository passkeyCredentialRepository,
            DeviceKeyCredentialRepository deviceKeyCredentialRepository,
            DeviceKeyService deviceKeyService,
            DeviceCredentialReplayGuard deviceCredentialReplayGuard,
            TOTPVerifier totpVerifier,
            @Qualifier("Argon2Hasher") Hasher hasher,
            UserServiceContract userService,
            PlatformTransactionSignerPort platformTransactionSigner,
            ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.passkeyInventoryService = passkeyInventoryService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.deviceKeyCredentialRepository = deviceKeyCredentialRepository;
        this.deviceKeyService = deviceKeyService;
        this.deviceCredentialReplayGuard = deviceCredentialReplayGuard;
        this.totpVerifier = totpVerifier;
        this.hasher = hasher;
        this.userService = userService;
        this.platformTransactionSigner = platformTransactionSigner;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves the acting user, checks resource ownership, verifies presented factors, enforces the
     * account-mode policy and optionally requests a platform signature for eligible scopes.
     *
     * @param request scoped authorization data and submitted factors
     * @return authorized account and optional platform signature
     * @throws AuthExceptions.AuthValidationException or structured factor errors when policy fails
     */
    @Override
    @Transactional
    public TransactionalAuthenticationResult authorize(TransactionalAuthenticationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Transactional authentication request is required.");
        }

        UserDataBase user = resolveUser(request);
        validateResourceOwnership(request, user);
        AccountSecurityType accountSecurity = resolveAccountSecurity(user);

        log.info(
                "Verifying transactional auth for user: {} (security: {}, scope: {})",
                user.getUsername(),
                accountSecurity,
                request.scope());

        if (request.scope() == TransactionalAuthenticationScope.KFE_CUSTODIAL_TRANSFER) {
            // Mobile/beta accounts enroll device-key (Ed25519) instead of WebAuthn passkeys.
            // Detect assertion kind first so WebAuthn parsers do not reject device-key JSON.
            boolean authorized;
            if (looksLikeDeviceKeyAssertion(request.passkeyAssertionJson())) {
                authorized = verifyDeviceKeyIfPresented(user, request.passkeyAssertionJson());
            } else {
                authorized = verifyPasskeyIfPresented(user, request.passkeyAssertionJson());
            }
            if (!authorized) {
                requirePasskey(user, false);
            }
            return new TransactionalAuthenticationResult(user, "");
        }
        if (request.scope() == TransactionalAuthenticationScope.KFE_COLD_WALLET_PSBT) {
            verifyRequiredTotp(user, request, "Codigo TOTP obrigatorio para operacao de carteira fria.");
            return new TransactionalAuthenticationResult(user, "");
        }

        boolean totpValid = false;
        if (request.scope() == TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            totpValid = verifyTotpIfRequiredOrPresented(user, request, accountSecurity);
        }
        boolean passphraseValid = verifyPassphraseIfPresented(user, request.confirmationPassphrase());
        if (request.scope() != TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            totpValid = verifyTotpIfRequiredOrPresented(user, request, accountSecurity);
        }
        // Mobile accounts use Device Key (Ed25519) for step-up, not WebAuthn passkeys.
        // WALLET_OUTBOUND (Lightning / on-chain send) must accept the same assertion shape as
        // KFE_CUSTODIAL_TRANSFER — otherwise DEVICE_KEY JSON is misparsed as WebAuthn → HTTP 400.
        boolean passkeyValid;
        if (looksLikeDeviceKeyAssertion(request.passkeyAssertionJson())) {
            passkeyValid = verifyDeviceKeyIfPresented(user, request.passkeyAssertionJson());
        } else {
            passkeyValid = verifyPasskeyIfPresented(user, request.passkeyAssertionJson());
        }

        enforceSecurityPolicy(user, accountSecurity, passphraseValid, totpValid, passkeyValid);

        String platformSignature = "";
        if (request.scope().platformSignatureRequired() && requiresPlatformSignature(accountSecurity)) {
            if (!platformTransactionSigner.isAvailable()) {
                throw new AuthExceptions.AuthValidationException(
                        "Advanced account security mode is configured, but platform co-signing is not available.");
            }
            platformSignature = platformTransactionSigner.sign(user);
        }

        return new TransactionalAuthenticationResult(user, platformSignature);
    }

    /** Resolves the supplied entity or loads the account referenced by the authenticated principal. */
    /** @param request authorization request */
    /** @return account acting on the protected operation */
    private UserDataBase resolveUser(TransactionalAuthenticationRequest request) {
        if (request.user() != null) {
            return request.user();
        }
        if (request.authenticatedUserId() == null) {
            throw new AuthExceptions.InvalidCredentials("Authenticated user is required for this operation.");
        }
        return userService.buscarPorId(request.authenticatedUserId())
                .orElseThrow(() -> new AuthExceptions.UserNotFoundException("Usuário não encontrado."));
    }

    /** Ensures an outbound resource owner matches both the resolved account and authenticated principal. */
    /** @param request authorization scope and ownership identifiers */
    /** @param user resolved actor */
    /** @throws IllegalArgumentException when owner identity is absent or mismatched */
    private void validateResourceOwnership(TransactionalAuthenticationRequest request, UserDataBase user) {
        if (request.scope() == TransactionalAuthenticationScope.WALLET_OUTBOUND
                && request.resourceOwnerUserId() == null) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
        if (request.resourceOwnerUserId() == null) {
            return;
        }
        if (user.getId() == null || !request.resourceOwnerUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
        if (request.authenticatedUserId() != null && !request.authenticatedUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
    }

    /** Defaults missing legacy account security settings to STANDARD mode. */
    /** @param user account */
    /** @return persisted mode or STANDARD */
    private AccountSecurityType resolveAccountSecurity(UserDataBase user) {
        return user.getAccountSecurity() != null ? user.getAccountSecurity() : AccountSecurityType.STANDARD;
    }

    /** Verifies a submitted confirmation passphrase, returning false when no passphrase is provided. */
    /** @param user account whose stored hash is checked */
    /** @param confirmationPassphrase optional submitted secret */
    /** @return true when a presented passphrase matches */
    private boolean verifyPassphraseIfPresented(UserDataBase user, String confirmationPassphrase) {
        if (!hasText(confirmationPassphrase)) {
            return false;
        }
        if (!hasher.verify(confirmationPassphrase.toCharArray(), user.getPassphrase())) {
            throw new AuthExceptions.InvalidPassphrase("Invalid passphrase for transaction authorization.");
        }
        log.info("Transaction passphrase factor verified for userId={}", user.getId());
        return true;
    }

    /** Applies scope/account TOTP requirements, selects the supplied or account secret, and verifies the code. */
    /** @param user resolved actor */
    /** @param request submitted factor data and scope */
    /** @param accountSecurity resolved account mode */
    /** @return true when TOTP was required/present and verified */
    private boolean verifyTotpIfRequiredOrPresented(
            UserDataBase user,
            TransactionalAuthenticationRequest request,
            AccountSecurityType accountSecurity) {
        boolean required = requiresTotp(accountSecurity);
        boolean presented = hasText(request.totpCode());
        if (!required && !(request.scope() == TransactionalAuthenticationScope.LEDGER_TRANSFER && presented)) {
            return false;
        }
        if (!presented) {
            throw missingTotpException(request.scope(), accountSecurity);
        }
        String totpSecret = hasText(request.totpSecret()) ? request.totpSecret() : user.getTOTPSecret();
        if (!hasText(totpSecret)) {
            throw new AuthExceptions.IncorrectTotpException("TOTP not configured for this operation.");
        }

        if (request.scope() == TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            if (!totpVerifier.totpMatcher(totpSecret, request.totpCode())) {
                throw new AuthExceptions.IncorrectTotpException("Invalid wallet TOTP code.");
            }
        } else {
            totpVerifier.totpVerify(totpSecret, request.totpCode());
        }
        log.info("Transaction TOTP factor verified for userId={}", user.getId());
        return true;
    }

    /** Requires and verifies a TOTP code for operations with unconditional TOTP requirements. */
    /** @param user resolved actor */
    /** @param request submitted factors and optional explicit secret */
    /** @param missingMessage caller-specific message when code is missing */
    /** @return true after successful verification */
    private boolean verifyRequiredTotp(
            UserDataBase user,
            TransactionalAuthenticationRequest request,
            String missingMessage) {
        if (!hasText(request.totpCode())) {
            throw new AuthExceptions.StructuredAuthException(
                    missingMessage,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTH_TRANSACTIONAL_AUTH_REQUIRED,
                    Map.of("required", "totpCode"));
        }
        String totpSecret = hasText(request.totpSecret()) ? request.totpSecret() : user.getTOTPSecret();
        if (!hasText(totpSecret)) {
            throw new AuthExceptions.IncorrectTotpException("TOTP not configured for this operation.");
        }
        totpVerifier.totpVerify(totpSecret, request.totpCode());
        log.info("Transaction TOTP factor verified for userId={}", user.getId());
        return true;
    }

    /** Creates the scope/mode-specific error used when a policy-required TOTP code was omitted. */
    /** @param scope protected operation scope */
    /** @param accountSecurity selected account mode */
    /** @return specific missing-factor exception */
    private AuthExceptions.IncorrectTotpException missingTotpException(
            TransactionalAuthenticationScope scope,
            AccountSecurityType accountSecurity) {
        if (scope == TransactionalAuthenticationScope.WALLET_OUTBOUND) {
            return new AuthExceptions.IncorrectTotpException("TOTP code is required for wallet authorization.");
        }
        if (accountSecurity == AccountSecurityType.SHAMIR) {
            return new AuthExceptions.IncorrectTotpException(
                    "A valid TOTP code is required for Shamir-protected transactions.");
        }
        return new AuthExceptions.IncorrectTotpException(
                "A valid TOTP code is required for multisig vault transactions.");
    }

    /** Verifies a WebAuthn assertion, ownership/status/origin, one-time challenge, and atomic counter advancement. */
    /** @param user resolved account requiring step-up */
    /** @param assertionJson optional JSON-encoded WebAuthn assertion */
    /** @return true when a passkey assertion was presented and accepted */
    private boolean verifyPasskeyIfPresented(UserDataBase user, String assertionJson) {
        if (!hasText(assertionJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(assertionJson);
            String signature = requiredText(node, "signature");
            String authData = requiredText(node, "authData");
            String clientDataJSON = requiredText(node, "clientDataJSON");
            String credentialId = requiredText(node, "credentialId");

            byte[] credentialIdBytes = CryptoUtils.decodeBase64(credentialId);
            String credentialRef = DeviceCredentialReplayGuard.credentialRefFromBytes(credentialIdBytes);

            log.info("Searching for passkey: userId={} credentialRef={}",
                    user.getId(), credentialRef);

            rejectIfReplayLocked(user, credentialRef);

            PasskeyVerificationProjection credential = passkeyCredentialRepository
                    .findVerificationByCredentialIdAndUserId(credentialIdBytes, user.getId())
                    .orElseThrow(() -> {
                        log.error("Passkey not found for userId={} credentialRef={}",
                                user.getId(), credentialRef);
                        return new AuthExceptions.StructuredAuthException(
                                "A passkey informada nao esta vinculada a este usuario.",
                                HttpStatus.CONFLICT,
                                ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND,
                                passkeyInventoryService.buildLinkNewPasskeyGuidance(
                                        user,
                                        "A passkey enviada nao pertence a esta conta. Vincule uma nova passkey."));
                    });

            if (!isActiveCredential(credential.status())) {
                throw new AuthExceptions.StructuredAuthException(
                        "Este dispositivo autenticado foi bloqueado ou revogado.",
                        HttpStatus.CONFLICT,
                        ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND,
                        passkeyInventoryService.buildLinkNewPasskeyGuidance(
                                user,
                                "Revise os dispositivos autenticados no app antes de tentar novamente."));
            }
            if (passkeyInventoryService.isKnownIncompatibleForCurrentLogin(
                    credential.relyingPartyId(), credential.originHost())) {
                throw new AuthExceptions.StructuredAuthException(
                        "Esta passkey foi vinculada a outro login/origem e nao pode autenticar aqui.",
                        HttpStatus.CONFLICT,
                        ErrorCodes.AUTH_PASSKEY_LINK_REQUIRED,
                        passkeyInventoryService.buildLinkNewPasskeyGuidance(
                                user,
                                "Entre com senha + TOTP e vincule uma nova passkey compativel com este dispositivo."));
            }

            String consumedChallenge = passkeyService.consumeChallengeFromRedis(user.getUsername());
            if (consumedChallenge == null) {
                log.warn("Passkey challenge expired or not found for userId={}", user.getId());
                String renewedChallenge = passkeyService.generateChallenge(user.getUsername());
                throw new AuthExceptions.StructuredAuthException(
                        "PASSKEY_CHALLENGE_REQUIRED:" + renewedChallenge,
                        HttpStatus.PRECONDITION_REQUIRED,
                        ErrorCodes.AUTH_PASSKEY_CHALLENGE,
                        passkeyInventoryService.buildChallengeRequired(
                                user,
                                renewedChallenge,
                                "O challenge da passkey expirou. Assine um novo challenge para continuar."));
            }

            PasskeyService.PasskeyVerificationResult verification = passkeyService.verifyAuthenticationAssertion(
                    user.getUsername(),
                    consumedChallenge,
                    signature,
                    credential.publicKeyCose(),
                    authData,
                    clientDataJSON);
            if (!verification.verified()) {
                String renewedChallenge = passkeyService.generateChallenge(user.getUsername());
                throw new AuthExceptions.StructuredAuthException(
                        "PASSKEY_CHALLENGE_REQUIRED:" + renewedChallenge,
                        HttpStatus.PRECONDITION_REQUIRED,
                        ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED,
                        passkeyInventoryService.buildChallengeRequired(
                                user,
                                renewedChallenge,
                                "A assertiva da passkey foi rejeitada. Gere uma nova assinatura e tente novamente."));
            }

            long newSignatureCount = verification.signatureCount();
            if (newSignatureCount <= credential.signatureCount()) {
                log.error("Passkey signature counter replay detected for userId={}. stored={} received={}",
                        user.getId(), credential.signatureCount(), newSignatureCount);
                throwReplayOrLock(user, credentialRef, "PASSKEY");
            }

            int updated = passkeyCredentialRepository.advanceSignatureCount(
                    credential.credentialId(),
                    user.getId(),
                    newSignatureCount);
            if (updated != 1) {
                log.error("Passkey signature counter atomic advance rejected for userId={} received={}",
                        user.getId(), newSignatureCount);
                throwReplayOrLock(user, credentialRef, "PASSKEY");
            }

            deviceCredentialReplayGuard.clearFailures(user.getId(), credentialRef);
            log.info(
                    LogDomain.AUTH,
                    "event=DEVICE_CREDENTIAL_STEP_UP_OK factor=PASSKEY assertionKind=WEBAUTHN_SHAPED userId={}",
                    user.getId());
            return true;
        } catch (AuthExceptions.StructuredAuthException exception) {
            throw exception;
        } catch (AuthExceptions.AuthValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Passkey verification failed during transactional authorization", exception);
            throw new AuthExceptions.StructuredAuthException(
                    "Falha ao validar a passkey desta operacao.",
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED,
                    passkeyInventoryService.buildLinkNewPasskeyGuidance(
                            user,
                            "Nao foi possivel validar a passkey enviada. Vincule outra passkey se o problema persistir."));
        }
    }

    /** Applies the account mode's exact factor threshold after factor checks have completed. */
    /** @param user resolved account */
    /** @param accountSecurity selected mode */
    /** @param passphraseValid whether passphrase was verified */
    /** @param totpValid whether TOTP was verified */
    /** @param passkeyValid whether passkey/device key was verified */
    private void enforceSecurityPolicy(
            UserDataBase user,
            AccountSecurityType accountSecurity,
            boolean passphraseValid,
            boolean totpValid,
            boolean passkeyValid) {
        switch (accountSecurity) {
            case PASSKEY, STANDARD -> requirePasskey(user, passkeyValid);
            case SHAMIR -> {
                if (!passphraseValid) {
                    throw new AuthExceptions.InvalidCredentials(
                            "This account requires confirmation reconstructed from your SLIP-39 shares.");
                }
                if (!totpValid) {
                    throw new AuthExceptions.IncorrectTotpException(
                            "A valid TOTP code is required for Shamir-protected transactions.");
                }
            }
            case MULTISIG_2FA -> {
                if (!passphraseValid) {
                    throw new AuthExceptions.InvalidCredentials(
                            "This multisig vault requires passphrase confirmation.");
                }
                if (!totpValid) {
                    throw new AuthExceptions.IncorrectTotpException(
                            "A valid TOTP code is required for multisig vault transactions.");
                }
                int threshold = user.getMultisigThreshold() != null ? user.getMultisigThreshold() : 2;
                if (threshold >= 3) {
                    requirePasskey(user, passkeyValid);
                }
            }
        }
    }

    /** Identifies modes whose transaction policy requires TOTP. */
    /** @param accountSecurity account mode */
    /** @return true for SHAMIR and MULTISIG_2FA */
    private boolean requiresTotp(AccountSecurityType accountSecurity) {
        return accountSecurity == AccountSecurityType.SHAMIR
                || accountSecurity == AccountSecurityType.MULTISIG_2FA;
    }

    /** Identifies account modes that require a platform co-signature when the scope requests it. */
    /** @param accountSecurity account mode */
    /** @return true for SHAMIR and MULTISIG_2FA */
    private boolean requiresPlatformSignature(AccountSecurityType accountSecurity) {
        return accountSecurity == AccountSecurityType.SHAMIR
                || accountSecurity == AccountSecurityType.MULTISIG_2FA;
    }

    /** Rejects when a policy requires a passkey/device-key assertion but none verified. */
    /** @param user account requiring the factor */
    /** @param passkeyValid assertion verification result */
    private void requirePasskey(UserDataBase user, boolean passkeyValid) {
        if (passkeyValid) {
            return;
        }
        String challenge = passkeyService.generateChallenge(user.getUsername());
        throw new AuthExceptions.StructuredAuthException(
                "PASSKEY_CHALLENGE_REQUIRED:" + challenge,
                HttpStatus.PRECONDITION_REQUIRED,
                ErrorCodes.AUTH_PASSKEY_CHALLENGE,
                passkeyInventoryService.buildChallengeRequired(
                        user,
                        challenge,
                        "Uma passkey compativel com este login e obrigatoria para concluir a operacao."));
    }

    /** Detects device-key assertion JSON so it is routed away from WebAuthn parsing. */
    /** @param assertionJson optional factor JSON */
    /** @return true for DEVICE_KEY or AUTH_DEVICE_KEY type discriminator */
    private boolean looksLikeDeviceKeyAssertion(String assertionJson) {
        if (!hasText(assertionJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(assertionJson);
            String type = node.path("type").asText("");
            return "DEVICE_KEY".equalsIgnoreCase(type) || "AUTH_DEVICE_KEY".equalsIgnoreCase(type);
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Accepts device-key AUTH assertions embedded in the passkeyAssertionJson field for
     * KFE custodial transfers (mobile onboarding path without WebAuthn passkeys).
     *
     * Expected JSON shape:
     * {
     *   "type": "DEVICE_KEY",
     *   "credentialId": "...",
     *   "deviceInstallId": "...",
     *   "signedPayload": "{...canonical AUTH_DEVICE_KEY...}",
     *   "signature": "..."
     * }
     * @param user resolved account
     * @param assertionJson optional device-key assertion
     * @return true when a device-key assertion is present and accepted
     */
    private boolean verifyDeviceKeyIfPresented(UserDataBase user, String assertionJson) {
        if (!hasText(assertionJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(assertionJson);
            String type = node.path("type").asText("");
            if (!"DEVICE_KEY".equalsIgnoreCase(type) && !"AUTH_DEVICE_KEY".equalsIgnoreCase(type)) {
                return false;
            }

            String credentialId = requiredText(node, "credentialId");
            String deviceInstallId = requiredText(node, "deviceInstallId");
            String signedPayload = requiredText(node, "signedPayload");
            String signature = requiredText(node, "signature");
            String credentialRef = DeviceCredentialReplayGuard.credentialRefFromString(credentialId);

            rejectIfReplayLocked(user, credentialRef);

            DeviceKeyCredential credential = deviceKeyCredentialRepository
                    .findByCredentialIdAndUserId(credentialId, user.getId())
                    .orElseThrow(() -> new AuthExceptions.StructuredAuthException(
                            "Device key not linked to this account.",
                            HttpStatus.CONFLICT,
                            ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND,
                            Map.of("required", "deviceKey")));

            DeviceKeyVerifyRequest verifyRequest = new DeviceKeyVerifyRequest();
            verifyRequest.setUsername(user.getUsername());
            verifyRequest.setCredentialId(credentialId);
            verifyRequest.setDeviceInstallId(deviceInstallId);
            verifyRequest.setSignedPayload(signedPayload);
            verifyRequest.setSignature(signature);

            long newCounter;
            try {
                newCounter = deviceKeyService.verifyAuthentication(verifyRequest, user, credential);
            } catch (DeviceKeyReplayException replayException) {
                throwReplayOrLock(user, credentialRef, "DEVICE_KEY");
                throw replayException; // unreachable; keep compiler happy
            }
            int updated = deviceKeyCredentialRepository.advanceCounter(
                    credentialId,
                    user.getId(),
                    newCounter,
                    LocalDateTime.now());
            if (updated != 1) {
                throwReplayOrLock(user, credentialRef, "DEVICE_KEY");
            }
            deviceCredentialReplayGuard.clearFailures(user.getId(), credentialRef);
            log.info(
                    LogDomain.AUTH,
                    "event=DEVICE_CREDENTIAL_STEP_UP_OK factor=DEVICE_KEY assertionKind=DEVICE_KEY userId={}",
                    user.getId());
            return true;
        } catch (AuthExceptions.StructuredAuthException exception) {
            throw exception;
        } catch (AuthExceptions.AuthValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Device key verification failed during transactional authorization: {}",
                    exception.getMessage(), exception);
            throw new AuthExceptions.StructuredAuthException(
                    "Falha ao validar a device key desta operacao: " + exception.getMessage(),
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED,
                    Map.of("required", "deviceKey",
                            "detail", exception.getMessage() == null ? "" : exception.getMessage()));
        }
    }

    /** Rejects a credential already within its replay soft-lock window. */
    /** @param user credential owner */
    /** @param credentialRef stable credential fingerprint */
    private void rejectIfReplayLocked(UserDataBase user, String credentialRef) {
        if (!deviceCredentialReplayGuard.isLocked(user.getId(), credentialRef)) {
            return;
        }
        throw new AuthExceptions.StructuredAuthException(
                "Chave do dispositivo temporariamente bloqueada por conflito de contador.",
                HttpStatus.LOCKED,
                ErrorCodes.AUTH_DEVICE_CRED_REPLAY_LOCKED,
                passkeyInventoryService.buildReplayLockedGuidance(
                        user,
                        deviceCredentialReplayGuard.lockSeconds()));
    }

    /** Records a replay failure and raises either a soft-lock or retryable counter-conflict error. */
    /** @param user credential owner */
    /** @param credentialRef stable credential fingerprint */
    /** @param factorKind PASSKEY or DEVICE_KEY */
    private void throwReplayOrLock(UserDataBase user, String credentialRef, String factorKind) {
        boolean locked = deviceCredentialReplayGuard.recordReplayFailure(
                user.getId(),
                credentialRef,
                factorKind);
        if (locked) {
            throw new AuthExceptions.StructuredAuthException(
                    "Chave do dispositivo temporariamente bloqueada por possivel conflito de seguranca.",
                    HttpStatus.LOCKED,
                    ErrorCodes.AUTH_DEVICE_CRED_REPLAY_LOCKED,
                    passkeyInventoryService.buildReplayLockedGuidance(
                            user,
                            deviceCredentialReplayGuard.lockSeconds()));
        }
        throw new AuthExceptions.StructuredAuthException(
                "O contador do autenticador nao avancou; a chave foi rejeitada por seguranca.",
                HttpStatus.CONFLICT,
                ErrorCodes.AUTH_PASSKEY_REPLAY,
                passkeyInventoryService.buildReplayConflictGuidance(
                        user,
                        "Possivel conflito de seguranca no contador da chave. Tente novamente; "
                                + "nao e necessario vincular outra chave neste passo."));
    }

    /** Extracts a required nonblank string field from assertion JSON. */
    /** @param node parsed JSON object */
    /** @param fieldName required property name */
    /** @return property text */
    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        if (!hasText(value)) {
            throw new AuthExceptions.AuthValidationException("Passkey assertion missing field: " + fieldName);
        }
        return value;
    }

    /** Checks whether a value contains non-whitespace text. */
    /** @param value candidate text */
    /** @return true for non-null and nonblank input */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Treats missing legacy status as active and accepts explicit ACTIVE case-insensitively. */
    /** @param status stored credential status */
    /** @return whether authentication may proceed for this credential */
    private boolean isActiveCredential(String status) {
        return status == null || status.isBlank() || "ACTIVE".equalsIgnoreCase(status);
    }
}
