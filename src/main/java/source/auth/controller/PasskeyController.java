package source.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.util.DevBalanceInjector;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.SignupState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;
import java.time.Duration;
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
    private final DevBalanceInjector balanceInjector;
    private final FinalizeSignupAccount finalizeSignupAccount;

    public PasskeyController(PasskeyService passkeyService,
                                  PasskeyCredentialRepository passkeyCredentialRepository,
                                  UserRepository userRepository,
                                  JwtServicer jwtServicer,
                                  SignupStateStore signupStateStore,
                                  DevBalanceInjector balanceInjector,
                                  FinalizeSignupAccount finalizeSignupAccount) {
        this.passkeyService = passkeyService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.userRepository = userRepository;
        this.jwtServicer = jwtServicer;
        this.signupStateStore = signupStateStore;
        this.balanceInjector = balanceInjector;
        this.finalizeSignupAccount = finalizeSignupAccount;
    }

    /**
     * Step 1: Request a challenge to sign using Ed25519 (Real Passkey implementation for Tor).
     */
    @GetMapping("/challenge")
    public ResponseEntity<ApiResponse<String>> getChallenge(@RequestParam String username) {
        String challenge = passkeyService.generateChallenge(username);
        return ResponseEntity.ok(ApiResponse.success("Passkey challenge generated", challenge));
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

            if (request.getSignature() == null || !passkeyService.verifySignature(
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

            credential.setDeviceName(request.getDeviceName());
            credential.setUser(user);
            credential.setSignatureCount(passkeyService.extractSignatureCount(request.getAuthData()));

            passkeyCredentialRepository.save(credential);
            return ResponseEntity.ok(ApiResponse.success("Passkey registered successfully", "OK"));

        } catch (Exception e) {
            log.error("Failed to register passkey", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), "REGISTRATION_ERROR"));
        }
    }

    /**
     * Step 2 (Option B): Verify signature and login via Passkey.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyAndLogin(@RequestBody PasskeyVerifyRequest request) {
        try {
            UserDataBase user = userRepository.findByUsername(request.getUsername());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

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

            Optional<PasskeyCredential> credOpt = passkeyCredentialRepository.findByCredentialIdAndUserId(credentialIdBytes, user.getId());
            if (credOpt.isEmpty()) {
                log.error("Possible Identity Squatting! User {} tried to use an unknown or unauthorized credentialId.", request.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid credential association.", "AUTH_FAILED"));
            }

            PasskeyCredential cred = credOpt.get();
            String consumedChallenge = passkeyService.consumeChallengeFromRedis(request.getUsername());
            if (consumedChallenge == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Passkey challenge expired or invalid", "CHALLENGE_EXPIRED"));
            }

            boolean verified = passkeyService.verifySignature(
                    request.getUsername(),
                    consumedChallenge,
                    request.getSignature(),
                    cred.getPublicKeyCose(),
                    request.getAuthData(),
                    request.getClientDataJSON());

            if (verified) {
                long newSignatureCount = passkeyService.extractSignatureCount(request.getAuthData());
                if (newSignatureCount <= cred.getSignatureCount()) {
                    log.error("Passkey signature counter replay detected for user {}. stored={} received={}",
                            request.getUsername(), cred.getSignatureCount(), newSignatureCount);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error("Passkey authenticator counter did not advance.",
                                    "SIGN_COUNT_REPLAY"));
                }
                cred.setSignatureCount(newSignatureCount);
                passkeyCredentialRepository.save(cred);

                // Ensure ledger exists for all wallets (Self-healing)
                finalizeSignupAccount.ensureUserFinancialsReady(user, null);

                if (Boolean.TRUE.equals(user.getIsActive())) {
                    balanceInjector.injectTestBalance(user);
                }

                String token = jwtServicer.generateToken(user.getId());
                return ResponseEntity.ok(ApiResponse.success("Passkey authentication successful", token));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid signature or challenge", "AUTH_FAILED"));
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
        if (request.getSignature() == null || !passkeyService.verifySignature(
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
        state.setPasskeyRegistered(true);

        signupStateStore.saveSignupState(sessionId, state, Duration.ofMinutes(1440));

        UserDataBase user = finalizeSignupAccount.execute(sessionId);
        String token = user.getId() + " " + jwtServicer.generateToken(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Passkey linked and account created.",
                token));
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
