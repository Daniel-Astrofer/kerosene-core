package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.service.account.BackupCodeService;
import source.auth.dto.BackupCodesStatusDTO;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/backup-codes")
public class BackupCodesController {

    private final BackupCodeService backupCodeService;

    public BackupCodesController(BackupCodeService backupCodeService) {
        this.backupCodeService = backupCodeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> getStatus(Authentication authentication) {
        BackupCodesStatusDTO response = backupCodeService.getStatus(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("Backup code status retrieved successfully.", response));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> regenerate(Authentication authentication) {
        BackupCodesStatusDTO response = backupCodeService.regenerate(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("Backup codes regenerated successfully.", response));
    }
}
