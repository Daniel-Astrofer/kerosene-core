package source.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.PasskeyVerificationProjection;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.util.DevBalanceInjector;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.PasskeyActionRequiredDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.dto.SignupState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;
import source.common.infra.logging.LogSanitizer;
import source.transactions.exception.ExternalPaymentsExceptions;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;


@RestController
@RequestMapping("/auth/passkey")
public class PasskeyController {

    private static final Logger log = LoggerFactory.getLogger(PasskeyController.class);
    private final PasskeyService passkeyService;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final UserRepository userRepository;
    private final JwtServicer jwtServicer;
    private final SignupStateStore signupStateStore;
    private final PasskeyInventoryService passkeyInventoryService;
    private final DevBalanceInjector balanceInjector;
    private final FinalizeSignupAccount finalizeSignupAccount;

    public PasskeyController(PasskeyService passkeyService,
                                  PasskeyCredentialRepository passkeyCredentialRepository,
                                  UserRepository userRepository,
                                  JwtServicer jwtServicer,
                                  SignupStateStore signupStateStore,
                                  PasskeyInventoryService passkeyInventoryService,
                                  DevBalanceInjector balanceInjector,
                                  FinalizeSignupAccount finalizeSignupAccount) {
        this.passkeyService = passkeyService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.userRepository = userRepository;
        this.jwtServicer = jwtServicer;
        this.signupStateStore = signupStateStore;
        this.passkeyInventoryService = passkeyInventoryService;
        this.balanceInjector = balanceInjector;
        this.finalizeSignupAccount = finalizeSignupAccount;
    }

    /**
     * Step 1: Request a challenge to sign using Ed25519 (Real Passkey implementation for Tor).
     */
    @GetMapping("/challenge")
    public ResponseEntity<ApiResponse<String>> getChallenge(@RequestParam String username) {
        String challenge = passkeyService.generateChallenge(normalizeUsername(username));
        return ResponseEntity.ok(ApiResponse.success("Passkey challenge generated", challenge));
    }

    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<PasskeyInventoryDTO>> getRegisteredDevices() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to inspect passkeys", "UNAUTHORIZED"));
        }

        UserDataBase user = userRepository.findById(Long.parseLong(auth.getName())).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", "USER_NOT_FOUND"));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Registered passkeys retrieved successfully.",
                passkeyInventoryService.inventoryFor(user)));
    }

    /**
     * Step 2 (Option A): Register a new passkey.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> registerPasskey(@RequestBody PasskeyRegistrationRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Must be logged in to register a passkey", "UNAUTHORIZED"));
            }

            UserDataBase user = userRepository.findById(Long.parseLong(auth.getName())).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

            ResponseEntity<ApiResponse<String>> invalidOrigin = rejectInvalidPasskeyOrigin(
                    user.getUsername(), request.getClientDataJSON());
            if (invalidOrigin != null) {
                return invalidOrigin;
            }

            // 1. Proof of Possession: Verify signature against challenge
            String consumedChallenge = passkeyService.consumeChallengeFromRedis(user.getUsername());
            if (consumedChallenge == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        ApiResponse.error("Registration challenge expired or invalid. Request a new one first.",
                                "CHALLENGE_EXPIRED"));
            }

            byte[] pkToVerify;
            java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
            try {
                pkToVerify = decoder.decode(request.getPublicKeyCose());
            } catch (Exception e) {
                pkToVerify = java.util.Base64.getUrlDecoder().decode(request.getPublicKeyCose());
            }

            if (request.getSignature() == null || !passkeyService.verifyRegistrationSignature(
                    user.getUsername(),
                    consumedChallenge,
                    request.getSignature(),
                    pkToVerify,
                    request.getAuthData(),
                    request.getClientDataJSON())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Proof of possession failed: Invalid signature or challenge",
                                "INVALID_SIGNATURE"));
            }

            // 2. Persist Credential
            PasskeyCredential credential = new PasskeyCredential();
            credential.setPublicKeyCose(pkToVerify);

            try {
                if (request.getCredentialId() != null) {
                    credential.setCredentialId(decoder.decode(request.getCredentialId()));
                }
                if (request.getUserHandle() != null) {
                    credential.setUserHandle(decoder.decode(request.getUserHandle()));
                }
            } catch (IllegalArgumentException e) {
                decoder = java.util.Base64.getUrlDecoder();
                if (request.getCredentialId() != null) {
                    credential.setCredentialId(decoder.decode(request.getCredentialId()));
                }
                if (request.getUserHandle() != null) {
                    credential.setUserHandle(decoder.decode(request.getUserHandle()));
                }
            }
            if (credential.getUserHandle() == null && credential.getCredentialId() != null) {
                credential.setUserHandle(credential.getCredentialId());
            }

            credential.setDeviceName(request.getDeviceName());
            applyPasskeyContextMetadata(credential, request);
            applyPasskeyDeviceMetadata(credential, request);
            credential.setUser(user);
            credential.setSignatureCount(passkeyService.extractSignatureCount(request.getAuthData()));

            passkeyCredentialRepository.save(credential);
            return ResponseEntity.ok(ApiResponse.success("Passkey registered successfully", "OK"));

        } catch (Exception e) {
            log.error("Failed to register passkey", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), "REGISTRATION_ERROR"));
        }
    }

    @PostMapping("/devices/{deviceInstallId}/block")
    public ResponseEntity<ApiResponse<PasskeyInventoryDTO>> blockDevice(@PathVariable String deviceInstallId) {
        return updateDeviceStatus(deviceInstallId, "BLOCKED", "Authenticated device blocked.");
    }

    @PostMapping("/devices/{deviceInstallId}/revoke")
    public ResponseEntity<ApiResponse<PasskeyInventoryDTO>> revokeDevice(@PathVariable String deviceInstallId) {
        return updateDeviceStatus(deviceInstallId, "REVOKED", "Authenticated device revoked.");
    }

    /**
     * Step 2 (Option B): Verify signature and login via Passkey.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Object>> verifyAndLogin(@RequestBody PasskeyVerifyRequest request) {
        try {
            String normalizedUsername = normalizeUsername(request.getUsername());
            if (request.getCredentialId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        ApiResponse.error("Frontend must send the credentialId for secure lookup.",
                                "MISSING_CREDENTIAL_ID"));
            }

            byte[] credentialIdBytes;
            try {
                credentialIdBytes = java.util.Base64.getUrlDecoder().decode(request.getCredentialId());
            } catch (Exception e) {
                credentialIdBytes = java.util.Base64.getDecoder().decode(request.getCredentialId());
            }

            UserDataBase user;
            Optional<PasskeyVerificationProjection> credOpt;
            if (normalizedUsername.isBlank()) {
                credOpt = passkeyCredentialRepository.findVerificationByCredentialId(credentialIdBytes);
                if (credOpt.isEmpty() || credOpt.get().userId() == null) {
                    log.error("Passkey credential not linked to any user for credentialRef={}",
                            LogSanitizer.fingerprint(credentialIdBytes));
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error(
                                    "A passkey enviada nao esta vinculada a nenhuma conta.",
                                    ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
                }
                user = userRepository.findById(credOpt.get().userId()).orElse(null);
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error("Passkey credential user no longer exists.",
                                    ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
                }
                normalizedUsername = normalizeUsername(user.getUsername());
            } else {
                user = userRepository.findByUsername(normalizedUsername);
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("User not found", "USER_NOT_FOUND"));
                }
                credOpt = passkeyCredentialRepository.findVerificationByCredentialIdAndUserId(credentialIdBytes, user.getId());
            }

            if (credOpt.isEmpty()) {
                log.error("Possible passkey identity squatting attempt for userRef={} credentialRef={}",
                        LogSanitizer.fingerprint(normalizedUsername),
                        LogSanitizer.fingerprint(credentialIdBytes));
                return passkeyLinkRequired(
                        user,
                        HttpStatus.UNAUTHORIZED,
                        ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND,
                        "A passkey enviada nao esta vinculada a esta conta.",
                        "Entre com senha + TOTP e vincule uma nova passkey para este dispositivo.");
            }

            PasskeyVerificationProjection cred = credOpt.get();
            if (!isActiveCredential(cred.status())) {
                return passkeyLinkRequired(
                        user,
                        HttpStatus.UNAUTHORIZED,
                        ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND,
                        "Este dispositivo autenticado foi bloqueado ou revogado.",
                        "Revise os dispositivos autenticados no app antes de tentar novamente.");
            }
            if (passkeyInventoryService.isKnownIncompatibleForCurrentLogin(cred.relyingPartyId(), cred.originHost())) {
                return passkeyLinkRequired(
                        user,
                        HttpStatus.CONFLICT,
                        ErrorCodes.AUTH_PASSKEY_LINK_REQUIRED,
                        "Esta passkey foi vinculada a outro login/origem e nao pode autenticar aqui.",
                        "Entre com senha + TOTP e vincule uma nova passkey compativel com este dispositivo.");
            }

            ResponseEntity<ApiResponse<Object>> invalidOrigin = rejectInvalidPasskeyOrigin(
                    normalizedUsername, request.getClientDataJSON());
            if (invalidOrigin != null) {
                return invalidOrigin;
            }

            String consumedChallenge = passkeyService.consumeChallengeFromRedis(normalizedUsername);
            if (consumedChallenge == null) {
                String renewedChallenge = passkeyService.generateChallenge(normalizedUsername);
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                        .body(ApiResponse.error(
                                "PASSKEY_CHALLENGE_REQUIRED:" + renewedChallenge,
                                ErrorCodes.AUTH_PASSKEY_CHALLENGE,
                                passkeyInventoryService.buildChallengeRequired(
                                        user,
                                        renewedChallenge,
                                        "O challenge da passkey expirou. Assine um novo challenge para continuar.")));
            }

            PasskeyService.PasskeyVerificationResult verification = passkeyService.verifyAuthenticationAssertion(
                    normalizedUsername,
                    consumedChallenge,
                    request.getSignature(),
                    cred.publicKeyCose(),
                    request.getAuthData(),
                    request.getClientDataJSON());

            if (verification.verified()) {
                long newSignatureCount = verification.signatureCount();
                if (newSignatureCount <= cred.signatureCount()) {
                    log.error("Passkey signature counter replay detected for userRef={}. stored={} received={}",
                            LogSanitizer.fingerprint(normalizedUsername), cred.signatureCount(), newSignatureCount);
                    return passkeyLinkRequired(
                            user,
                            HttpStatus.UNAUTHORIZED,
                            ErrorCodes.AUTH_PASSKEY_REPLAY,
                            "O contador do autenticador nao avancou; esta passkey foi rejeitada.",
                            "Vincule outra passkey ou repita o login com TOTP.");
                }
                int updated = passkeyCredentialRepository.advanceSignatureCount(
                        cred.credentialId(),
                        user.getId(),
                        newSignatureCount);
                if (updated != 1) {
                    log.error("Passkey signature counter atomic advance rejected for userRef={} received={}",
                            LogSanitizer.fingerprint(normalizedUsername), newSignatureCount);
                    return passkeyLinkRequired(
                            user,
                            HttpStatus.UNAUTHORIZED,
                            ErrorCodes.AUTH_PASSKEY_REPLAY,
                            "O contador do autenticador nao avancou; esta passkey foi rejeitada.",
                            "Vincule outra passkey ou repita o login com TOTP.");
                }

                // Ensure ledger exists for all wallets (Self-healing)
                finalizeSignupAccount.ensureUserFinancialsReady(user, null);

                if (Boolean.TRUE.equals(user.getIsActive())) {
                    balanceInjector.injectTestBalance(user);
                }

                String token = jwtServicer.generateToken(user.getId());
                return ResponseEntity.ok(ApiResponse.success("Passkey authentication successful", token));
            } else {
                return passkeyLinkRequired(
                        user,
                        HttpStatus.UNAUTHORIZED,
                        ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED,
                        "A assinatura da passkey ou o challenge foram rejeitados.",
                        "Se esta passkey nao estiver disponivel neste dispositivo, entre com TOTP e vincule outra.");
            }

        } catch (Exception e) {
            log.error("Passkey verification failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), "VERIFY_ERROR"));
        }
    }

    // --- Onboarding Flow Integration ---

    @PostMapping("/onboarding/start")
    public ResponseEntity<ApiResponse<String>> startOnboardingRegistration(@RequestParam String sessionId) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session expired", "SESSION_NOT_FOUND"));
        }

        String challenge = passkeyService.generateChallenge(state.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Onboarding challenge generated", challenge));
    }

    @PostMapping("/onboarding/finish")
    public ResponseEntity<ApiResponse<String>> finishOnboardingRegistration(@RequestParam String sessionId,
                                                                           @RequestBody PasskeyRegistrationRequest request) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session expired", "SESSION_NOT_FOUND"));
        }

        ResponseEntity<ApiResponse<String>> invalidOrigin = rejectInvalidPasskeyOrigin(
                state.getUsername(), request.getClientDataJSON());
        if (invalidOrigin != null) {
            return invalidOrigin;
        }

        String challenge = passkeyService.consumeChallengeFromRedis(state.getUsername());
        if (challenge == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Registration challenge expired or invalid", "CHALLENGE_EXPIRED"));
        }

        byte[] pkToVerify;
        if (request.getPublicKeyCose() != null) {
            try {
                pkToVerify = java.util.Base64.getDecoder().decode(request.getPublicKeyCose());
            } catch (Exception e) {
                pkToVerify = java.util.Base64.getUrlDecoder().decode(request.getPublicKeyCose());
            }
        } else {
            try {
                pkToVerify = java.util.Base64.getDecoder().decode(request.getPublicKey());
            } catch (Exception e) {
                pkToVerify = java.util.Base64.getUrlDecoder().decode(request.getPublicKey());
            }
        }

        // Verify signature to prove possession of the private key
        if (request.getSignature() == null || !passkeyService.verifyRegistrationSignature(
                state.getUsername(),
                challenge,
                request.getSignature(),
                pkToVerify,
                request.getAuthData(),
                request.getClientDataJSON())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Proof of possession failed: Invalid signature or challenge",
                            "INVALID_SIGNATURE"));
        }

        state.setPasskeyPublicKey(request.getPublicKey());
        state.setPasskeyDeviceName(request.getDeviceName());
        state.setPasskeyCredentialId(request.getCredentialId());
        state.setPasskeyUserHandle(request.getUserHandle());
        state.setPasskeyPublicKeyCose(request.getPublicKeyCose());
        state.setPasskeyRelyingPartyId(resolveRelyingPartyIdFromProof(request));
        state.setPasskeyOriginHost(passkeyService.extractOriginHostFromClientData(request.getClientDataJSON()));
        state.setPasskeyBrand(request.getBrand());
        state.setPasskeyModel(request.getModel());
        state.setPasskeySerialNumber(request.getSerialNumber());
        state.setPasskeyDeviceInstallId(request.getDeviceInstallId());
        state.setPasskeyPlatform(request.getPlatform());
        state.setPasskeyBrowser(request.getBrowser());
        state.setPasskeyRegistered(true);

        signupStateStore.saveSignupState(sessionId, state, Duration.ofMinutes(1440));

        UserDataBase user;
        try {
            user = finalizeSignupAccount.execute(sessionId);
        } catch (ExternalPaymentsExceptions.CustodyProviderUnavailable
                 | FinalizeSignupAccount.VaultNotReadyException exception) {
            log.warn("Passkey onboarding finalization is temporarily unavailable for sessionRef={} userRef={}: {}",
                    LogSanitizer.fingerprint(sessionId),
                    LogSanitizer.fingerprint(state.getUsername()),
                    exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Passkey onboarding finalization failed for sessionRef={} userRef={}",
                    LogSanitizer.fingerprint(sessionId),
                    LogSanitizer.fingerprint(state.getUsername()),
                    exception);
            throw exception;
        }
        String token = user.getId() + " " + jwtServicer.generateToken(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Passkey linked and account created.",
                token));
    }

    private <T> ResponseEntity<ApiResponse<T>> rejectInvalidPasskeyOrigin(String username, String clientDataJSON) {
        if (passkeyService.isClientDataOriginAllowed(clientDataJSON)) {
            return null;
        }

        log.warn("Rejected passkey clientData origin for userRef={} originRef={}",
                LogSanitizer.fingerprint(username),
                LogSanitizer.fingerprint(passkeyService.extractOriginFromClientData(clientDataJSON)));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<T>error(
                        "Passkey origin is not allowed for this app build.",
                ErrorCodes.AUTH_PASSKEY_INVALID_ORIGIN));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseEntity<ApiResponse<Object>> passkeyLinkRequired(
            UserDataBase user,
            HttpStatus status,
            String errorCode,
            String message,
            String reason) {
        PasskeyActionRequiredDTO data = passkeyInventoryService.buildLinkNewPasskeyGuidance(user, reason);
        return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode, data));
    }

    private ResponseEntity<ApiResponse<PasskeyInventoryDTO>> updateDeviceStatus(
            String deviceInstallId,
            String status,
            String message) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to update devices", "UNAUTHORIZED"));
        }

        UserDataBase user = userRepository.findById(Long.parseLong(auth.getName())).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", "USER_NOT_FOUND"));
        }

        Optional<PasskeyCredential> credential = passkeyCredentialRepository
                .findFirstByUserIdAndDeviceInstallId(user.getId(), deviceInstallId);
        if (credential.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Device not found", "DEVICE_NOT_FOUND"));
        }

        PasskeyCredential device = credential.get();
        device.setStatus(status);
        passkeyCredentialRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success(message, passkeyInventoryService.inventoryFor(user)));
    }

    private void applyPasskeyContextMetadata(PasskeyCredential credential, PasskeyRegistrationRequest request) {
        credential.setRelyingPartyId(resolveRelyingPartyIdFromProof(request));
        credential.setOriginHost(passkeyService.extractOriginHostFromClientData(request.getClientDataJSON()));
    }

    private String resolveRelyingPartyIdFromProof(PasskeyRegistrationRequest request) {
        String matchedRpId = passkeyService.resolveRelyingPartyIdFromAuthenticatorData(
                request.getAuthData(),
                request.getClientDataJSON());
        return firstNonBlank(
                matchedRpId,
                passkeyService.resolveRelyingPartyIdFromClientData(request.getClientDataJSON()));
    }

    private void applyPasskeyDeviceMetadata(PasskeyCredential credential, PasskeyRegistrationRequest request) {
        credential.setBrand(request.getBrand());
        credential.setModel(request.getModel());
        credential.setSerialNumber(request.getSerialNumber());
        credential.setDeviceInstallId(request.getDeviceInstallId());
        credential.setPlatform(request.getPlatform());
        credential.setBrowser(request.getBrowser());
        credential.setStatus(firstNonBlank(request.getStatus(), "ACTIVE"));
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean isActiveCredential(String status) {
        return status == null || status.isBlank() || "ACTIVE".equalsIgnoreCase(status);
    }

    public static class PasskeyRegistrationRequest {
        private String publicKey;
        private String deviceName;
        private String signature; // Added for verification
        private String authData; // WebAuthn authenticatorData
        private String clientDataJSON; // WebAuthn clientDataJSON
        private String credentialId;
        private String userHandle;
        private String publicKeyCose;
        private String brand;
        private String model;
        private String serialNumber;
        private String deviceInstallId;
        private String platform;
        private String browser;
        private String status;

        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public String getAuthData() { return authData; }
        public void setAuthData(String authData) { this.authData = authData; }
        public String getClientDataJSON() { return clientDataJSON; }
        public void setClientDataJSON(String clientDataJSON) { this.clientDataJSON = clientDataJSON; }
        public String getCredentialId() { return credentialId; }
        public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
        public String getUserHandle() { return userHandle; }
        public void setUserHandle(String userHandle) { this.userHandle = userHandle; }
        public String getPublicKeyCose() { return publicKeyCose; }
        public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public String getDeviceInstallId() { return deviceInstallId; }
        public void setDeviceInstallId(String deviceInstallId) { this.deviceInstallId = deviceInstallId; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getBrowser() { return browser; }
        public void setBrowser(String browser) { this.browser = browser; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PasskeyVerifyRequest {
        private String username;
        private String signature;
        private String authData;
        private String clientDataJSON;
        private String credentialId;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public String getAuthData() { return authData; }
        public void setAuthData(String authData) { this.authData = authData; }
        public String getClientDataJSON() { return clientDataJSON; }
        public void setClientDataJSON(String clientDataJSON) { this.clientDataJSON = clientDataJSON; }
        public String getCredentialId() { return credentialId; }
        public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    }
}
