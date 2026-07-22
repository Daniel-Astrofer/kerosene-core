package source.auth.application.usecase.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.security.profile.AdvancedAccountSecurityAvailability;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.AccountSecurityUpdateRequestDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.common.exception.ErrorCodes;

@Component
public class UpdateAccountSecurityProfileUseCase {

    private final UserServiceContract userService;
    private final PasskeyInventoryService passkeyInventoryService;
    private final AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability;
    private final AppPinService appPinService;

    public UpdateAccountSecurityProfileUseCase(
            UserServiceContract userService,
            PasskeyInventoryService passkeyInventoryService,
            AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability,
            AppPinService appPinService) {
        this.userService = userService;
        this.passkeyInventoryService = passkeyInventoryService;
        this.advancedAccountSecurityAvailability = advancedAccountSecurityAvailability;
        this.appPinService = appPinService;
    }

    @Transactional
    public AccountSecurityProfileDTO execute(
            UserDataBase user,
            AccountSecurityUpdateRequestDTO request,
            String deviceHash) {
        validateAndApply(user, request);
        UserDataBase persistedUser = userService.createUserInDataBase(user);

        PasskeyInventoryDTO passkeys = passkeyInventoryService.inventoryFor(persistedUser);
        return AccountSecurityProfileDTO.fromUser(
                persistedUser,
                passkeys.passkeyRegistered(),
                passkeys,
                appPinService.getStatus(persistedUser, deviceHash));
    }

    private void validateAndApply(
            UserDataBase user,
            AccountSecurityUpdateRequestDTO request) {
        AccountSecurityType mode = request.getAccountSecurity() != null
                ? request.getAccountSecurity()
                : AccountSecurityType.STANDARD;
        advancedAccountSecurityAvailability.assertSupported(mode);

        switch (mode) {
            case SHAMIR -> applyShamir(user, request);
            case MULTISIG_2FA -> applyMultisig(user, request);
            case PASSKEY -> applyPasskey(user);
            case STANDARD -> applyStandard(user);
        }
    }

    private void applyShamir(
            UserDataBase user,
            AccountSecurityUpdateRequestDTO request) {
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

    private void applyMultisig(
            UserDataBase user,
            AccountSecurityUpdateRequestDTO request) {
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

    private void applyPasskey(UserDataBase user) {
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

    private void applyStandard(UserDataBase user) {
        user.setAccountSecurity(AccountSecurityType.STANDARD);
        user.setShamirTotalShares(null);
        user.setShamirThreshold(null);
        user.setMultisigThreshold(2);
    }
}
