package source.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.usecase.passkey.GetPasskeyInventoryUseCase;
import source.auth.application.usecase.passkey.UpdatePasskeyDeviceStatusUseCase;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.orchestrator.passkey.PasskeyOrchestrator;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.dto.SignupState;
import source.auth.dto.passkey.PasskeyRegistrationRequest;
import source.auth.dto.passkey.PasskeyVerifyRequest;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

@RestController
@RequestMapping("/auth/passkey")
public class PasskeyController {

    private static final Logger log = LoggerFactory.getLogger(PasskeyController.class);
    private final PasskeyService passkeyService;
    private final JwtServicer jwtServicer;
    private final SignupStateStore signupStateStore;
    private final FinalizeSignupAccount finalizeSignupAccount;
    private final RedisServicer redisService;
    private final PasskeyOrchestrator passkeyOrchestrator;
    private final GetPasskeyInventoryUseCase getPasskeyInventoryUseCase;
    private final UpdatePasskeyDeviceStatusUseCase updatePasskeyDeviceStatusUseCase;

    public PasskeyController(PasskeyService passkeyService,
                                  JwtServicer jwtServicer,
                                  SignupStateStore signupStateStore,
                                  FinalizeSignupAccount finalizeSignupAccount,
                                  RedisServicer redisService,
                                  PasskeyOrchestrator passkeyOrchestrator,
                                  GetPasskeyInventoryUseCase getPasskeyInventoryUseCase,
                                  UpdatePasskeyDeviceStatusUseCase updatePasskeyDeviceStatusUseCase) {
        this.passkeyService = passkeyService;
        this.jwtServicer = jwtServicer;
        this.signupStateStore = signupStateStore;
        this.finalizeSignupAccount = finalizeSignupAccount;
        this.redisService = redisService;
        this.passkeyOrchestrator = passkeyOrchestrator;
        this.getPasskeyInventoryUseCase = getPasskeyInventoryUseCase;
        this.updatePasskeyDeviceStatusUseCase = updatePasskeyDeviceStatusUseCase;
    }

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
                    .body(ApiResponse.error("Must be logged in to inspect passkeys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        GetPasskeyInventoryUseCase.Result result = getPasskeyInventoryUseCase.execute(Long.parseLong(auth.getName()));
        if (result.status() == GetPasskeyInventoryUseCase.Status.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.message(), ErrorCodes.AUTH_USER_NOT_FOUND));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Registered passkeys retrieved successfully.",
                result.inventory()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> registerPasskey(@RequestBody PasskeyRegistrationRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to register a passkey", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        return passkeyOrchestrator.registerPasskey(Long.parseLong(auth.getName()), request);
    }

    @PostMapping("/devices/{deviceInstallId}/block")
    public ResponseEntity<ApiResponse<PasskeyInventoryDTO>> blockDevice(@PathVariable String deviceInstallId) {
        return updateDeviceStatus(deviceInstallId, "BLOCKED", "Authenticated device blocked.");
    }

    @PostMapping("/devices/{deviceInstallId}/revoke")
    public ResponseEntity<ApiResponse<PasskeyInventoryDTO>> revokeDevice(@PathVariable String deviceInstallId) {
        return updateDeviceStatus(deviceInstallId, "REVOKED", "Authenticated device revoked.");
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Object>> verifyAndLogin(@RequestBody PasskeyVerifyRequest request) {
        return passkeyOrchestrator.verifyAndLogin(request);
    }

    @PostMapping("/onboarding/start")
    public ResponseEntity<ApiResponse<String>> startOnboardingRegistration(@RequestParam String sessionId) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session expired", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        String challenge = passkeyService.generateChallenge(state.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Onboarding challenge generated", challenge));
    }

    @PostMapping("/onboarding/finish")
    public ResponseEntity<ApiResponse<String>> finishOnboardingRegistration(@RequestParam String sessionId,
                                                                           @RequestBody PasskeyRegistrationRequest request) {
        return passkeyOrchestrator.finishOnboardingRegistration(sessionId, request);
    }

    private ResponseEntity<ApiResponse<PasskeyInventoryDTO>> updateDeviceStatus(
            String deviceInstallId,
            String status,
            String message) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to update devices", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        UpdatePasskeyDeviceStatusUseCase.Result result = updatePasskeyDeviceStatusUseCase.execute(
                Long.parseLong(auth.getName()),
                deviceInstallId,
                status);
        if (result.status() == UpdatePasskeyDeviceStatusUseCase.Status.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.message(), ErrorCodes.AUTH_USER_NOT_FOUND));
        }
        if (result.status() == UpdatePasskeyDeviceStatusUseCase.Status.DEVICE_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.message(), ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
        }

        return ResponseEntity.ok(ApiResponse.success(message, result.inventory()));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
