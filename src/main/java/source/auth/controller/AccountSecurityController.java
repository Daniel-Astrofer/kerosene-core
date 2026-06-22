package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.AuthExceptions;
import source.auth.application.usecase.security.UpdateAccountSecurityProfileUseCase;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.AccountSecurityUpdateRequestDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/security")
public class AccountSecurityController {

    private final UserServiceContract userService;
    private final PasskeyInventoryService passkeyInventoryService;
    private final AppPinService appPinService;
    private final UpdateAccountSecurityProfileUseCase updateAccountSecurityProfileUseCase;

    public AccountSecurityController(
            UserServiceContract userService,
            PasskeyInventoryService passkeyInventoryService,
            AppPinService appPinService,
            UpdateAccountSecurityProfileUseCase updateAccountSecurityProfileUseCase) {
        this.userService = userService;
        this.passkeyInventoryService = passkeyInventoryService;
        this.appPinService = appPinService;
        this.updateAccountSecurityProfileUseCase = updateAccountSecurityProfileUseCase;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<AccountSecurityProfileDTO>> getProfile(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash) {
        UserDataBase user = getAuthenticatedUser();
        PasskeyInventoryDTO passkeys = passkeyInventoryService.inventoryFor(user);
        boolean passkeyAvailable = passkeys.passkeyRegistered();
        return ResponseEntity.ok(ApiResponse.success(
                "Account security profile retrieved successfully.",
                AccountSecurityProfileDTO.fromUser(
                        user,
                        passkeyAvailable,
                        passkeys,
                        appPinService.getStatus(user, deviceHash))));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<AccountSecurityProfileDTO>> updateProfile(
            @RequestHeader(value = "X-Device-Hash", required = false) String deviceHash,
            @RequestBody AccountSecurityUpdateRequestDTO request) {
        UserDataBase user = getAuthenticatedUser();
        return ResponseEntity.ok(ApiResponse.success(
                "Account security profile updated successfully.",
                updateAccountSecurityProfileUseCase.execute(user, request, deviceHash)));
    }

    private UserDataBase getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthExceptions.InvalidCredentials("Not authenticated.");
        }

        try {
            Long userId = Long.parseLong(auth.getName());
            return userService.buscarPorId(userId)
                    .orElseThrow(() -> new AuthExceptions.InvalidCredentials("Authenticated user not found."));
        } catch (NumberFormatException e) {
            throw new AuthExceptions.InvalidCredentials("Invalid authentication context.");
        }
    }
}
