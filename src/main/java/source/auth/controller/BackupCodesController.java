package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.AuthExceptions;
import source.auth.application.service.account.BackupCodeService;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.dto.BackupCodesStatusDTO;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/backup-codes")
public class BackupCodesController {

    private final BackupCodeService backupCodeService;
    private final TransactionalAuthenticationPort transactionalAuthenticationPort;

    public BackupCodesController(
            BackupCodeService backupCodeService,
            TransactionalAuthenticationPort transactionalAuthenticationPort) {
        this.backupCodeService = backupCodeService;
        this.transactionalAuthenticationPort = transactionalAuthenticationPort;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> getStatus(Authentication authentication) {
        BackupCodesStatusDTO response = backupCodeService.getStatus(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("Backup code status retrieved successfully.", response));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> regenerate(
            Authentication authentication,
            @RequestBody(required = false) RegenerateBackupCodesRequest request) {
        Long userId = Long.parseLong(authentication.getName());
        RegenerateBackupCodesRequest stepUpRequest = request != null ? request : RegenerateBackupCodesRequest.empty();
        try {
            transactionalAuthenticationPort.authorize(TransactionalAuthenticationRequest.accountSecurityChange(
                    userId,
                    stepUpRequest.totpCode(),
                    stepUpRequest.passkeyAssertionJson(),
                    stepUpRequest.confirmationPassphrase()));
        } catch (AuthExceptions.AuthValidationException exception) {
            throw new AuthExceptions.InvalidCredentials("Unable to authorize backup code regeneration.");
        }

        BackupCodesStatusDTO response = backupCodeService.regenerate(userId);
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
