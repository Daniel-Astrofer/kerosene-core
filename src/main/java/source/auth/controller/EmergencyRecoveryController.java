package source.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.AuthExceptions;
import source.auth.application.orchestrator.recovery.EmergencyRecoveryUseCase;
import source.auth.dto.EmergencyRecoveryFinishRequest;
import source.auth.dto.EmergencyRecoveryFinishResponse;
import source.auth.dto.EmergencyRecoveryStartRequest;
import source.auth.dto.EmergencyRecoveryStartResponse;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/recovery/emergency")
public class EmergencyRecoveryController {

    private final EmergencyRecoveryUseCase emergencyRecoveryUseCase;

    public EmergencyRecoveryController(EmergencyRecoveryUseCase emergencyRecoveryUseCase) {
        this.emergencyRecoveryUseCase = emergencyRecoveryUseCase;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<EmergencyRecoveryStartResponse>> start(
            @RequestBody EmergencyRecoveryStartRequest request,
            HttpServletRequest httpRequest) {
        try {
            String clientFingerprint = EmergencyRecoveryUseCase.buildClientFingerprint(httpRequest);
            EmergencyRecoveryStartResponse response = emergencyRecoveryUseCase.start(request, clientFingerprint);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success("Emergency recovery session created.", response));
        } catch (AuthExceptions.RecoveryRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(false, e.getMessage(), null, "RECOVERY_RATE_LIMITED"));
        } catch (AuthExceptions.RecoveryRejectedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, e.getMessage(), null, "RECOVERY_REJECTED"));
        } catch (AuthExceptions.InvalidCredentials | AuthExceptions.InvalidPassphrase
                | AuthExceptions.UsernameCantBeNull | AuthExceptions.InvalidCharacterUsername
                | AuthExceptions.CharacterLimitException | AuthExceptions.PassphraseCantBeNull
                | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null, "RECOVERY_BAD_REQUEST"));
        }
    }

    @PostMapping("/finish")
    public ResponseEntity<ApiResponse<EmergencyRecoveryFinishResponse>> finish(
            @RequestBody EmergencyRecoveryFinishRequest request) {
        try {
            EmergencyRecoveryFinishResponse response = emergencyRecoveryUseCase.finish(request);
            return ResponseEntity.ok(ApiResponse.success(
                    "Emergency recovery completed. Login again with the new credentials.", response));
        } catch (AuthExceptions.RecoverySessionExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(new ApiResponse<>(false, e.getMessage(), null, "RECOVERY_SESSION_EXPIRED"));
        } catch (AuthExceptions.RecoveryRejectedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, e.getMessage(), null, "RECOVERY_REJECTED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null, "RECOVERY_BAD_REQUEST"));
        }
    }
}
