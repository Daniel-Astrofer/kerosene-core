package source.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.service.devicekey.DeviceKeyChallengeException;
import source.auth.application.service.devicekey.DeviceKeyProtocolException;
import source.auth.application.service.devicekey.DeviceKeyReplayException;
import source.auth.application.usecase.devicekey.FinishAuthenticatedDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.FinishOnboardingDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.GetDeviceKeyAuthenticationChallengeUseCase;
import source.auth.application.usecase.devicekey.ManageDeviceKeyDevicesUseCase;
import source.auth.application.usecase.devicekey.StartAuthenticatedDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.StartOnboardingDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.VerifyDeviceKeyLoginUseCase;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.dto.devicekey.DeviceKeyDeviceDTO;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.dto.devicekey.DeviceKeyVerifyRequest;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;
import source.common.exception.FinancialProviderUnavailableException;

import java.util.List;

@RestController
@RequestMapping("/auth/device-key")
public class DeviceKeyController {

    private static final Logger log = LoggerFactory.getLogger(DeviceKeyController.class);
    private static final String DEVICE_KEY_GENERIC_ERROR = "Device key request failed.";
    private static final String DEVICE_KEY_CHALLENGE_ERROR = "Device key challenge is required or expired.";
    private static final String DEVICE_KEY_ASSERTION_ERROR = "Device key assertion could not be verified.";
    private static final String DEVICE_KEY_REPLAY_ERROR = "Device key request was rejected by replay protection.";

    private final GetDeviceKeyAuthenticationChallengeUseCase getDeviceKeyAuthenticationChallengeUseCase;
    private final ManageDeviceKeyDevicesUseCase manageDeviceKeyDevicesUseCase;
    private final StartOnboardingDeviceKeyRegistrationUseCase startOnboardingDeviceKeyRegistrationUseCase;
    private final StartAuthenticatedDeviceKeyRegistrationUseCase startAuthenticatedDeviceKeyRegistrationUseCase;
    private final FinishAuthenticatedDeviceKeyRegistrationUseCase finishAuthenticatedDeviceKeyRegistrationUseCase;
    private final FinishOnboardingDeviceKeyRegistrationUseCase finishOnboardingDeviceKeyRegistrationUseCase;
    private final VerifyDeviceKeyLoginUseCase verifyDeviceKeyLoginUseCase;

    public DeviceKeyController(
            GetDeviceKeyAuthenticationChallengeUseCase getDeviceKeyAuthenticationChallengeUseCase,
            ManageDeviceKeyDevicesUseCase manageDeviceKeyDevicesUseCase,
            StartOnboardingDeviceKeyRegistrationUseCase startOnboardingDeviceKeyRegistrationUseCase,
            StartAuthenticatedDeviceKeyRegistrationUseCase startAuthenticatedDeviceKeyRegistrationUseCase,
            FinishAuthenticatedDeviceKeyRegistrationUseCase finishAuthenticatedDeviceKeyRegistrationUseCase,
            FinishOnboardingDeviceKeyRegistrationUseCase finishOnboardingDeviceKeyRegistrationUseCase,
            VerifyDeviceKeyLoginUseCase verifyDeviceKeyLoginUseCase) {
        this.getDeviceKeyAuthenticationChallengeUseCase = getDeviceKeyAuthenticationChallengeUseCase;
        this.manageDeviceKeyDevicesUseCase = manageDeviceKeyDevicesUseCase;
        this.startOnboardingDeviceKeyRegistrationUseCase = startOnboardingDeviceKeyRegistrationUseCase;
        this.startAuthenticatedDeviceKeyRegistrationUseCase = startAuthenticatedDeviceKeyRegistrationUseCase;
        this.finishAuthenticatedDeviceKeyRegistrationUseCase = finishAuthenticatedDeviceKeyRegistrationUseCase;
        this.finishOnboardingDeviceKeyRegistrationUseCase = finishOnboardingDeviceKeyRegistrationUseCase;
        this.verifyDeviceKeyLoginUseCase = verifyDeviceKeyLoginUseCase;
    }

    @PostMapping("/onboarding/start")
    public ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> startOnboardingRegistration(
            @RequestParam String sessionId,
            @RequestParam(required = false) String username) {
        StartOnboardingDeviceKeyRegistrationUseCase.Result result =
                startOnboardingDeviceKeyRegistrationUseCase.execute(sessionId, username);
        if (result.status() == StartOnboardingDeviceKeyRegistrationUseCase.Status.SESSION_EXPIRED) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session expired", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        return ResponseEntity.ok(ApiResponse.success("Device key challenge generated", result.challenge()));
    }

    @PostMapping("/onboarding/finish")
    public ResponseEntity<ApiResponse<String>> finishOnboardingRegistration(
            @RequestParam String sessionId,
            @RequestBody DeviceKeyRegistrationRequest request) {
        try {
            FinishOnboardingDeviceKeyRegistrationUseCase.Result result =
                    finishOnboardingDeviceKeyRegistrationUseCase.execute(sessionId, request);
            if (result.status() == FinishOnboardingDeviceKeyRegistrationUseCase.Status.SESSION_EXPIRED) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Session expired", ErrorCodes.AUTH_SESSION_EXPIRED));
            }
            return ResponseEntity.ok(ApiResponse.success(
                    "Device key linked and account created.",
                    result.token()));
        } catch (DeviceKeyChallengeException exception) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.error(DEVICE_KEY_CHALLENGE_ERROR, ErrorCodes.AUTH_PASSKEY_CHALLENGE));
        } catch (DeviceKeyProtocolException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(DEVICE_KEY_ASSERTION_ERROR, ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED));
        } catch (FinancialProviderUnavailableException
                 | FinalizeSignupAccount.VaultNotReadyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Device key onboarding failed", exception);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(DEVICE_KEY_GENERIC_ERROR, ErrorCodes.AUTH_GENERIC));
        }
    }

    @GetMapping("/challenge")
    public ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> getChallenge(@RequestParam String username) {
        GetDeviceKeyAuthenticationChallengeUseCase.Result result =
                getDeviceKeyAuthenticationChallengeUseCase.execute(username);
        if (result.status() == GetDeviceKeyAuthenticationChallengeUseCase.Status.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.message(), ErrorCodes.AUTH_USER_NOT_FOUND));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Device key challenge generated",
                result.challenge()));
    }

    @PostMapping("/register/start")
    public ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> startAuthenticatedRegistration() {
        Long userId = authenticatedUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to register a device key", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        StartAuthenticatedDeviceKeyRegistrationUseCase.Result result =
                startAuthenticatedDeviceKeyRegistrationUseCase.execute(userId);
        if (result.status() == StartAuthenticatedDeviceKeyRegistrationUseCase.Status.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to register a device key", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Device key registration challenge generated",
                result.challenge()));
    }

    @PostMapping("/register/finish")
    public ResponseEntity<ApiResponse<String>> finishAuthenticatedRegistration(
            @RequestBody DeviceKeyRegistrationRequest request) {
        Long userId = authenticatedUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to register a device key", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        try {
            FinishAuthenticatedDeviceKeyRegistrationUseCase.Result result =
                    finishAuthenticatedDeviceKeyRegistrationUseCase.execute(userId, request);
            if (result.status() == FinishAuthenticatedDeviceKeyRegistrationUseCase.Status.USER_NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Must be logged in to register a device key", ErrorCodes.AUTH_SESSION_EXPIRED));
            }
            return ResponseEntity.ok(ApiResponse.success("Device key registered successfully", "OK"));
        } catch (DeviceKeyChallengeException exception) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.error(DEVICE_KEY_CHALLENGE_ERROR, ErrorCodes.AUTH_PASSKEY_CHALLENGE));
        } catch (DeviceKeyProtocolException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(DEVICE_KEY_ASSERTION_ERROR, ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Object>> verifyAndLogin(@RequestBody DeviceKeyVerifyRequest request) {
        try {
            VerifyDeviceKeyLoginUseCase.Result result = verifyDeviceKeyLoginUseCase.execute(request);
            return mapVerifyResult(result);
        } catch (DeviceKeyReplayException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(DEVICE_KEY_REPLAY_ERROR, ErrorCodes.AUTH_PASSKEY_REPLAY));
        } catch (DeviceKeyChallengeException exception) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.error(DEVICE_KEY_CHALLENGE_ERROR, ErrorCodes.AUTH_PASSKEY_CHALLENGE));
        } catch (DeviceKeyProtocolException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(DEVICE_KEY_ASSERTION_ERROR, ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED));
        } catch (RuntimeException exception) {
            log.error("Device key verification failed", exception);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(DEVICE_KEY_GENERIC_ERROR, ErrorCodes.AUTH_GENERIC));
        }
    }

    private ResponseEntity<ApiResponse<Object>> mapVerifyResult(VerifyDeviceKeyLoginUseCase.Result result) {
        return switch (result.status()) {
            case INVALID_CREDENTIAL_ID -> ResponseEntity.badRequest()
                    .body(ApiResponse.error("credentialId is required", ErrorCodes.SYS_INVALID_ARGUMENTS));
            case CREDENTIAL_NOT_FOUND -> credentialNotFound();
            case USER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", ErrorCodes.AUTH_USER_NOT_FOUND));
            case REPLAY_COUNTER_NOT_ADVANCED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "Device key counter did not advance.",
                            ErrorCodes.AUTH_PASSKEY_REPLAY));
            case INACTIVE_ACCOUNT -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Account is inactive", ErrorCodes.AUTH_INVALID_CREDENTIALS));
            case TOTP_REQUIRED -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success("Device key verified. TOTP required.", result.data()));
            case AUTHENTICATED -> ResponseEntity.ok(ApiResponse.success("Device key authentication successful", result.data()));
        };
    }

    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> getRegisteredDevices() {
        Long userId = authenticatedUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to inspect device keys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        ManageDeviceKeyDevicesUseCase.Result result = manageDeviceKeyDevicesUseCase.listDevices(userId);
        if (result.status() == ManageDeviceKeyDevicesUseCase.Status.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to inspect device keys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        return ResponseEntity.ok(ApiResponse.success("Registered device keys retrieved.", result.devices()));
    }

    @PostMapping("/devices/{credentialId}/revoke")
    public ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> revokeDevice(@PathVariable String credentialId) {
        Long userId = authenticatedUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to revoke device keys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        ManageDeviceKeyDevicesUseCase.Result result = manageDeviceKeyDevicesUseCase.revokeDevice(userId, credentialId);
        if (result.status() == ManageDeviceKeyDevicesUseCase.Status.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to revoke device keys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        if (result.status() == ManageDeviceKeyDevicesUseCase.Status.CREDENTIAL_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Device key not found", ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
        }

        return ResponseEntity.ok(ApiResponse.success("Device key revoked.", result.devices()));
    }

    private ResponseEntity<ApiResponse<Object>> credentialNotFound() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        "Esta chave deste dispositivo nao esta vinculada a conta.",
                        ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
    }

    private Long authenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return null;
        }
        return Long.parseLong(auth.getName());
    }

}
