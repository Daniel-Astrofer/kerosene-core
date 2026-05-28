package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.service.account.AccountSecurityStatusService;
import source.auth.dto.AccountSecurityStatusDTO;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/security-status")
public class AccountSecurityStatusController {

    private final AccountSecurityStatusService accountSecurityStatusService;

    public AccountSecurityStatusController(AccountSecurityStatusService accountSecurityStatusService) {
        this.accountSecurityStatusService = accountSecurityStatusService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AccountSecurityStatusDTO>> getStatus(Authentication authentication) {
        AccountSecurityStatusDTO status =
                accountSecurityStatusService.getStatus(Long.parseLong(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success("Account security status retrieved successfully.", status));
    }
}
