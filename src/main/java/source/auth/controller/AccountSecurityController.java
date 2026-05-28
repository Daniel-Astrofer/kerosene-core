package source.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.auth.AuthExceptions;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.security.profile.AdvancedAccountSecurityAvailability;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.AccountSecurityUpdateRequestDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

@RestController
@RequestMapping("/auth/security")
public class AccountSecurityController {

    private final UserServiceContract userService;
    private final PasskeyInventoryService passkeyInventoryService;
    private final AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability;
    private final AppPinService appPinService;

    public AccountSecurityController(
            UserServiceContract userService,
            PasskeyInventoryService passkeyInventoryService,
            AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability,
            AppPinService appPinService) {
        this.userService = userService;
        this.passkeyInventoryService = passkeyInventoryService;
        this.advancedAccountSecurityAvailability = advancedAccountSecurityAvailability;
        this.appPinService = appPinService;
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
        PasskeyInventoryDTO passkeys = passkeyInventoryService.inventoryFor(user);

        validateAndApply(user, request);
        user = userService.createUserInDataBase(user);

        passkeys = passkeyInventoryService.inventoryFor(user);
        return ResponseEntity.ok(ApiResponse.success(
                "Account security profile updated successfully.",
                AccountSecurityProfileDTO.fromUser(
                        user,
                        passkeys.passkeyRegistered(),
                        passkeys,
                        appPinService.getStatus(user, deviceHash))));
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
            AccountSecurityUpdateRequestDTO request) {
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
                if (multisigThreshold == 3 && !passkeyInventoryService.hasUsablePasskeyForCurrentLogin(user)) {
                    throw new AuthExceptions.StructuredAuthException(
                            "Nenhuma passkey compativel com este login esta vinculada a conta.",
                            HttpStatus.CONFLICT,
                            ErrorCodes.AUTH_PASSKEY_LINK_REQUIRED,
                            passkeyInventoryService.buildLinkNewPasskeyGuidance(
                                    user,
                                    "Vincule uma passkey deste dispositivo antes de ativar multisig 3FA."));
                }

                user.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
                user.setShamirTotalShares(null);
                user.setShamirThreshold(null);
                user.setMultisigThreshold(multisigThreshold);
            }
            case PASSKEY -> {
                if (!passkeyInventoryService.hasUsablePasskeyForCurrentLogin(user)) {
                    throw new AuthExceptions.StructuredAuthException(
                            "Nenhuma passkey compativel com este login esta vinculada a conta.",
                            HttpStatus.CONFLICT,
                            ErrorCodes.AUTH_PASSKEY_LINK_REQUIRED,
                            passkeyInventoryService.buildLinkNewPasskeyGuidance(
                                    user,
                                    "Vincule uma passkey deste dispositivo antes de ativar protecao por passkey."));
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
