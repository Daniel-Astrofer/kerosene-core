package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.usecase.totp.TotpOperationsUseCase;
import source.auth.dto.BackupCodesStatusDTO;
import source.auth.dto.TotpSetupResponseDTO;
import source.common.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/auth/totp")
public class TotpController {

    private final TotpOperationsUseCase totpOperationsUseCase;

    public TotpController(TotpOperationsUseCase totpOperationsUseCase) {
        this.totpOperationsUseCase = totpOperationsUseCase;
    }

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TotpSetupResponseDTO>> setup(Authentication authentication) {
        TotpSetupResponseDTO response = totpOperationsUseCase.setup(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("TOTP setup secret generated successfully.", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<BackupCodesStatusDTO>> verify(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        BackupCodesStatusDTO response = totpOperationsUseCase.verify(
                Long.parseLong(authentication.getName()),
                request.get("totpCode"));
        return ResponseEntity.ok(ApiResponse.success("TOTP enabled successfully.", response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> disable(Authentication authentication) {
        totpOperationsUseCase.disable(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("TOTP disabled successfully.", "OK"));
    }
}
