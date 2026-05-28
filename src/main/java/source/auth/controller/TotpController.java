package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.service.account.TotpManagementService;
import source.auth.dto.BackupCodesStatusDTO;
import source.auth.dto.TotpSetupResponseDTO;
import source.common.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/auth/totp")
public class TotpController {

    private final TotpManagementService totpManagementService;

    public TotpController(TotpManagementService totpManagementService) {
        this.totpManagementService = totpManagementService;
    }

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TotpSetupResponseDTO>> setup(Authentication authentication) {
        TotpSetupResponseDTO response =
                totpManagementService.beginSetup(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("TOTP setup secret generated successfully.", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> verify(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        BackupCodesStatusDTO response = totpManagementService.verifySetup(
                Long.parseLong(authentication.getName()),
                request.get("totpCode"));
        return ResponseEntity.ok(ApiResponse.success("TOTP enabled successfully.", response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> disable(Authentication authentication) {
        totpManagementService.disable(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("TOTP disabled successfully.", "OK"));
    }
}
