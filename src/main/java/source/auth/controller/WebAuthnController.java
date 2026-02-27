package source.auth.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.service.webauthn.WebAuthnService;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.dto.SignupState;
import source.common.dto.ApiResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for FIDO2/WebAuthn Passkey registration and login.
 */
@RestController
@RequestMapping("/auth/passkey")
public class WebAuthnController {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnController.class);
    private final WebAuthnService webAuthnService;
    private final JwtServicer jwtServicer;
    private final RedisContract redisContract;

    // Temporary storage for pending requests.
    // In production, this must be a distributed cache like Redis to support
    // multi-node scaling.
    private final Map<String, String> pendingRegistrations = new ConcurrentHashMap<>();
    private final Map<String, String> pendingLogins = new ConcurrentHashMap<>();

    public WebAuthnController(WebAuthnService webAuthnService, JwtServicer jwtServicer, RedisContract redisContract) {
        this.webAuthnService = webAuthnService;
        this.jwtServicer = jwtServicer;
        this.redisContract = redisContract;
    }

    @PostMapping("/register/start")
    public ResponseEntity<ApiResponse<String>> startRegistration() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("UNAUTHORIZED", "Must be logged in to register a passkey"));
            }

            Long userId = Long.parseLong(auth.getName());
            // Normally, you'd lookup the actual username here to pass to startRegistration.
            // Placeholder: "user" + userId.
            String username = "user" + userId;

            String optionsJson = webAuthnService.startRegistration(username);

            // Store the raw options JSON in memory temporarily so we can pass it to
            // finishRegistration later
            pendingRegistrations.put(username, optionsJson);

            return ResponseEntity.ok(ApiResponse.success("Registration options generated", optionsJson));

        } catch (Exception e) {
            log.error("Failed to start passkey registration", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("WEBAUTHN_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/register/finish")
    public ResponseEntity<ApiResponse<String>> finishRegistration(@RequestBody String responseJson) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("UNAUTHORIZED", "Not logged in"));
            }

            String username = "user" + auth.getName();
            String creationOptionsJson = pendingRegistrations.remove(username);

            if (creationOptionsJson == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("WEBAUTHN_ERROR", "No pending registration found or it expired"));
            }

            webAuthnService.finishRegistration(username, creationOptionsJson, responseJson);
            return ResponseEntity.ok(ApiResponse.success("Passkey registered successfully", "OK"));
        } catch (Exception e) {
            log.error("Failed to finish passkey registration", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("WEBAUTHN_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/login/start")
    public ResponseEntity<ApiResponse<String>> startLogin(@RequestParam String username) {
        try {
            String assertionOptionsJson = webAuthnService.startLogin(username);
            pendingLogins.put(username, assertionOptionsJson);
            return ResponseEntity.ok(ApiResponse.success("Login options generated", assertionOptionsJson));
        } catch (Exception e) {
            log.error("Failed to start passkey login", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("WEBAUTHN_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/login/finish")
    public ResponseEntity<ApiResponse<String>> finishLogin(@RequestParam String username,
            @RequestBody String responseJson) {
        try {
            String assertionRequestJson = pendingLogins.remove(username);
            if (assertionRequestJson == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("WEBAUTHN_ERROR", "No pending login found or it expired"));
            }

            boolean success = webAuthnService.finishLogin(assertionRequestJson, responseJson);
            if (success) {
                // If login succeeds via Passkey, bypass TOTP phase entirely and issue a fresh
                // JWT
                // Assuming username format is "{username}" mapped to userId in auth/login
                long userId = Long.parseLong(username.replace("user", ""));

                // Device Hash is no longer used, passing empty string or null is fine.
                String token = jwtServicer.generateToken(userId, "");
                return ResponseEntity.ok(ApiResponse.success("Passkey login successful", token));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("WEBAUTHN_ERROR", "Login verification failed"));
            }
        } catch (Exception e) {
            log.error("Failed to finish passkey login", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("WEBAUTHN_ERROR", e.getMessage()));
        }
    }

    // --- Onboarding Endpoints (Pre-database insertion) --- //

    @PostMapping("/register/onboarding/start")
    public ResponseEntity<ApiResponse<String>> startOnboardingRegistration(@RequestParam String sessionId) {
        try {
            SignupState state = redisContract.findSignupState(sessionId);
            if (state == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("SESSION_NOT_FOUND", "Signup session expired or invalid"));
            }
            if (!state.isTotpVerified()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("UNAUTHORIZED", "Must finish TOTP phase first"));
            }

            // We use the raw username during signup as ID representation temporarily
            String username = "user_" + state.getUsername();
            String optionsJson = webAuthnService.startRegistration(username);

            pendingRegistrations.put(username, optionsJson);
            return ResponseEntity.ok(ApiResponse.success("Onboarding passkey options generated", optionsJson));

        } catch (Exception e) {
            log.error("Failed to start onboarding passkey registration", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("WEBAUTHN_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/register/onboarding/finish")
    public ResponseEntity<ApiResponse<String>> finishOnboardingRegistration(@RequestParam String sessionId,
            @RequestBody String responseJson) {
        try {
            SignupState state = redisContract.findSignupState(sessionId);
            if (state == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("SESSION_NOT_FOUND", "Signup session expired or invalid"));
            }

            String username = "user_" + state.getUsername();
            String creationOptionsJson = pendingRegistrations.remove(username);

            if (creationOptionsJson == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("WEBAUTHN_ERROR", "No pending registration found or it expired"));
            }

            // WebAuthnService will normally attempt to save to PasskeyRepository.
            // But since the user doesn't exist in DB yet, we need a special method
            // handling.
            String credentialJson = webAuthnService.finishOnboardingRegistration(username, creationOptionsJson,
                    responseJson);

            // Save credential object temporarily in Redis until 3-Confirmations
            state.setPasskeyCredentialJson(credentialJson);
            state.setPasskeyRegistered(true);
            redisContract.saveSignupState(sessionId, state, 1440);

            return ResponseEntity.ok(ApiResponse.success("Passkey attached to Onboarding Session", "OK"));
        } catch (Exception e) {
            log.error("Failed to finish onboarding passkey registration", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("WEBAUTHN_ERROR", e.getMessage()));
        }
    }
}
