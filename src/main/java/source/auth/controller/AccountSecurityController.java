package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.service.security.profile.AdvancedAccountSecurityAvailability;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.AccountSecurityUpdateRequestDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/auth/security")
public class AccountSecurityController {

    private final UserServiceContract userService;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability;

    public AccountSecurityController(
            UserServiceContract userService,
            PasskeyCredentialRepository passkeyCredentialRepository,
            AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability) {
        this.userService = userService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.advancedAccountSecurityAvailability = advancedAccountSecurityAvailability;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<AccountSecurityProfileDTO>> getProfile() {
        UserDataBase user = getAuthenticatedUser();
        boolean passkeyAvailable = !passkeyCredentialRepository.findByUserId(user.getId()).isEmpty();
        return ResponseEntity.ok(ApiResponse.success(
                "Account security profile retrieved successfully.",
                AccountSecurityProfileDTO.fromUser(user, passkeyAvailable)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<AccountSecurityProfileDTO>> updateProfile(
            @RequestBody AccountSecurityUpdateRequestDTO request) {
        UserDataBase user = getAuthenticatedUser();
        boolean passkeyAvailable = !passkeyCredentialRepository.findByUserId(user.getId()).isEmpty();

        validateAndApply(user, request, passkeyAvailable);
        user = userService.createUserInDataBase(user);

        return ResponseEntity.ok(ApiResponse.success(
                "Account security profile updated successfully.",
                AccountSecurityProfileDTO.fromUser(user, passkeyAvailable)));
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

    private void validateAndApply(
            UserDataBase user,
            AccountSecurityUpdateRequestDTO request,
            boolean passkeyAvailable) {
        AccountSecurityType mode = request.getAccountSecurity() != null
                ? request.getAccountSecurity()
                : AccountSecurityType.STANDARD;
        advancedAccountSecurityAvailability.assertSupported(mode);

        switch (mode) {
            case SHAMIR -> {
                if (request.getShamirTotalShares() == null || request.getShamirThreshold() == null) {
                    throw new AuthExceptions.InvalidCredentials(
                            "Shamir mode requires total shares and threshold.");
                }
                if (request.getShamirTotalShares() < 2 || request.getShamirTotalShares() > 8) {
                    throw new AuthExceptions.InvalidCredentials(
                            "Shamir total shares must stay between 2 and 8.");
                }
                if (request.getShamirThreshold() < 2
                        || request.getShamirThreshold() > request.getShamirTotalShares()) {
                    throw new AuthExceptions.InvalidCredentials(
                            "Shamir threshold must be between 2 and total shares.");
                }

                user.setAccountSecurity(AccountSecurityType.SHAMIR);
                user.setShamirTotalShares(request.getShamirTotalShares());
                user.setShamirThreshold(request.getShamirThreshold());
                user.setMultisigThreshold(2);
            }
            case MULTISIG_2FA -> {
                int multisigThreshold = request.getMultisigThreshold() != null ? request.getMultisigThreshold() : 2;
                if (multisigThreshold < 2 || multisigThreshold > 3) {
                    throw new AuthExceptions.InvalidCredentials(
                            "Multisig threshold must be 2 or 3 factors.");
                }
                if (multisigThreshold == 3 && !passkeyAvailable) {
                    throw new AuthExceptions.InvalidCredentials(
                            "A registered passkey is required to enable a 3-factor multisig vault.");
                }

                user.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
                user.setShamirTotalShares(null);
                user.setShamirThreshold(null);
                user.setMultisigThreshold(multisigThreshold);
            }
            case PASSKEY -> {
                if (!passkeyAvailable) {
                    throw new AuthExceptions.InvalidCredentials(
                            "A registered passkey is required before switching to passkey-only protection.");
                }
                user.setAccountSecurity(AccountSecurityType.PASSKEY);
                user.setShamirTotalShares(null);
                user.setShamirThreshold(null);
                user.setMultisigThreshold(2);
            }
            case STANDARD -> {
                user.setAccountSecurity(AccountSecurityType.STANDARD);
                user.setShamirTotalShares(null);
                user.setShamirThreshold(null);
                user.setMultisigThreshold(2);
            }
        }
    }
}
