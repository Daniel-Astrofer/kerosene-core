package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.usecase.backupcodes.BackupCodesOperationsUseCase;
import source.auth.dto.BackupCodesStatusDTO;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/backup-codes")
public class BackupCodesController {

    private final BackupCodesOperationsUseCase backupCodesOperationsUseCase;

    public BackupCodesController(BackupCodesOperationsUseCase backupCodesOperationsUseCase) {
        this.backupCodesOperationsUseCase = backupCodesOperationsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> getStatus(Authentication authentication) {
        BackupCodesStatusDTO response = backupCodesOperationsUseCase.getStatus(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("Backup code status retrieved successfully.", response));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> regenerate(
            Authentication authentication,
            @RequestBody(required = false) RegenerateBackupCodesRequest request) {
        Long userId = Long.parseLong(authentication.getName());
        RegenerateBackupCodesRequest stepUpRequest = request != null ? request : RegenerateBackupCodesRequest.empty();
        BackupCodesStatusDTO response = backupCodesOperationsUseCase.regenerate(
                userId,
                stepUpRequest.totpCode(),
                stepUpRequest.passkeyAssertionJson(),
                stepUpRequest.confirmationPassphrase());
        return ResponseEntity.ok(ApiResponse.success("Backup codes regenerated successfully.", response));
    }

    public record RegenerateBackupCodesRequest(
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {

        private static RegenerateBackupCodesRequest empty() {
            return new RegenerateBackupCodesRequest(null, null, null);
        }
    }
}
