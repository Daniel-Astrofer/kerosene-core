package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.application.service.account.AccountActivationService;
import source.auth.dto.AccountActivationStatusDTO;
import source.common.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/auth/activation-status")
public class AccountActivationController {

    private final AccountActivationService accountActivationService;

    public AccountActivationController(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AccountActivationStatusDTO>> getStatus(Authentication authentication) {
        AccountActivationStatusDTO status = accountActivationService.getStatus(authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success("Activation status retrieved successfully.", status));
    }

    @PostMapping("/funding-link")
    public ResponseEntity<ApiResponse<AccountActivationStatusDTO>> createFundingLink(Authentication authentication) {
        AccountActivationStatusDTO status =
                accountActivationService.createOrReuseLink(authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                "Initial funding is prepared inside the KFE flow.",
                status));
    }

    @PostMapping("/{linkId}/confirm")
    public ResponseEntity<ApiResponse<AccountActivationStatusDTO>> confirm(
            @PathVariable String linkId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        AccountActivationStatusDTO status = accountActivationService.confirm(
                authenticatedUserId(authentication),
                linkId,
                request.get("txid"),
                request.get("fromAddress"));
        return ResponseEntity.ok(ApiResponse.success("Activation status retrieved successfully.", status));
    }

    private Long authenticatedUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
