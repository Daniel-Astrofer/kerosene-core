package source.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.infra.persistance.jpa.HardwareCredentialRepository;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.application.service.hardware.HardwareAuthService;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.SignupState;
import source.auth.model.entity.HardwareCredential;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;


@RestController
@RequestMapping("/auth/hardware")
public class HardwareAuthController {

    private static final Logger log = LoggerFactory.getLogger(HardwareAuthController.class);
    private final HardwareAuthService hardwareAuthService;
    private final HardwareCredentialRepository hardwareCredentialRepository;
    private final UserRepository userRepository;
    private final JwtServicer jwtServicer;
    private final RedisContract redisContract;

    public HardwareAuthController(HardwareAuthService hardwareAuthService,
                                  HardwareCredentialRepository hardwareCredentialRepository,
                                  UserRepository userRepository,
                                  JwtServicer jwtServicer,
                                  RedisContract redisContract) {
        this.hardwareAuthService = hardwareAuthService;
        this.hardwareCredentialRepository = hardwareCredentialRepository;
        this.userRepository = userRepository;
        this.jwtServicer = jwtServicer;
        this.redisContract = redisContract;
    }

    /**
     * Step 1: Request a challenge to sign.
     */
    @GetMapping("/challenge")
    public ResponseEntity<ApiResponse<String>> getChallenge(@RequestParam String username) {
        String challenge = hardwareAuthService.generateChallenge(username);
        return ResponseEntity.ok(ApiResponse.success("Challenge generated", challenge));
    }

    /**
     * Step 2 (Option A): Register a new hardware key for an authenticated user.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> registerHardwareKey(@RequestBody HardwareRegistrationRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("UNAUTHORIZED", "Must be logged in to register a hardware key"));
            }

            UserDataBase user = userRepository.findById(Long.parseLong(auth.getName())).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("USER_NOT_FOUND", "User not found"));
            }

            HardwareCredential credential = new HardwareCredential();
            credential.setPublicKey(request.getPublicKey());
            credential.setDeviceName(request.getDeviceName());
            credential.setUser(user);

            hardwareCredentialRepository.save(credential);
            return ResponseEntity.ok(ApiResponse.success("Hardware key registered successfully", "OK"));

        } catch (Exception e) {
            log.error("Failed to register hardware key", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("REGISTRATION_ERROR", e.getMessage()));
        }
    }

    /**
     * Step 2 (Option B): Verify signature and login.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyAndLogin(@RequestBody HardwareVerifyRequest request) {
        try {
            // Find user by username
            UserDataBase user = userRepository.findByUsername(request.getUsername());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("USER_NOT_FOUND", "User not found"));
            }

            // Find all hardware credentials for this user
            var credentials = hardwareCredentialRepository.findByUserId(user.getId());
            
            boolean verified = false;
            for (HardwareCredential cred : credentials) {
                if (hardwareAuthService.verifySignature(request.getUsername(), request.getSignature(), cred.getPublicKey())) {
                    verified = true;
                    // Update signature count to prevent some types of replay, though challenge handles most
                    cred.setSignatureCount(cred.getSignatureCount() + 1);
                    hardwareCredentialRepository.save(cred);
                    break;
                }
            }

            if (verified) {
                String token = jwtServicer.generateToken(user.getId());
                return ResponseEntity.ok(ApiResponse.success("Hardware authentication successful", token));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("AUTH_FAILED", "Invalid signature or challenge"));
            }

        } catch (Exception e) {
            log.error("Hardware verification failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("VERIFY_ERROR", e.getMessage()));
        }
    }

    // --- Onboarding Flow Integration ---

    @PostMapping("/register/onboarding/start")
    public ResponseEntity<ApiResponse<String>> startOnboardingRegistration(@RequestParam String sessionId) {
        SignupState state = redisContract.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("SESSION_NOT_FOUND", "Session expired"));
        }
        
        String challenge = hardwareAuthService.generateChallenge(state.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Onboarding challenge generated", challenge));
    }

    @PostMapping("/register/onboarding/finish")
    public ResponseEntity<ApiResponse<String>> finishOnboardingRegistration(@RequestParam String sessionId,
                                                                          @RequestBody HardwareRegistrationRequest request) {
        SignupState state = redisContract.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("SESSION_NOT_FOUND", "Session expired"));
        }

        // For simplicity, let's just store the public key in the session state.
        state.setHardwarePublicKey(request.getPublicKey());
        state.setHardwareDeviceName(request.getDeviceName());
        state.setPasskeyRegistered(true); // Treat hardware auth as fulfilling this requirement
        
        redisContract.saveSignupState(sessionId, state, 1440);
        
        return ResponseEntity.ok(ApiResponse.success("Hardware key linked to onboarding", "OK"));
    }

    // DTOs (could be in separate files)
    public static class HardwareRegistrationRequest {
        private String publicKey;
        private String deviceName;
        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    }

    public static class HardwareVerifyRequest {
        private String username;
        private String signature;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
    }
}
