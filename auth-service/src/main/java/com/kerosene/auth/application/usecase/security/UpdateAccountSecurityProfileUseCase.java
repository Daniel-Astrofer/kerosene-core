package com.kerosene.auth.application.usecase.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.account.AppPinService;
import com.kerosene.auth.application.service.passkey.PasskeyInventoryService;
import com.kerosene.auth.application.service.security.profile.AdvancedAccountSecurityAvailability;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.dto.AccountSecurityProfileDTO;
import com.kerosene.auth.dto.AccountSecurityUpdateRequestDTO;
import com.kerosene.auth.dto.PasskeyInventoryDTO;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.auth.model.enums.AccountSecurityType;
import com.kerosene.common.exception.ErrorCodes;

/** Validates and persists an account security mode, then returns its refreshed security profile. */
@Component
public class UpdateAccountSecurityProfileUseCase {

    /** Persists the modified account entity. */
    private final UserServiceContract userService;
    /** Builds the updated public inventory and checks mode prerequisites. */
    private final PasskeyInventoryService passkeyInventoryService;
    /** Rejects security modes that are disabled or unavailable in this deployment. */
    private final AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability;
    /** Resolves device-specific PIN status included in the returned profile. */
    private final AppPinService appPinService;

    /** Creates the account security profile update operation. */
    /** @param userService persistence boundary */
    /** @param passkeyInventoryService passkey prerequisite and projection service */
    /** @param advancedAccountSecurityAvailability mode availability policy */
    /** @param appPinService device-scoped PIN status service */
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

    /** Validates the requested mode, updates and persists account security fields, then returns the profile. */
    /** @param user account entity selected by the authenticated boundary */
    /** @param request requested mode and mode-specific thresholds */
    /** @param deviceHash device reference used to include PIN status */
    /** @return persisted account security profile */
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

    /** Selects the requested mode (STANDARD when omitted), checks feature availability, and applies its fields. */
    /** @param user account to mutate */
    /** @param request requested security configuration */
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

    /** Applies SHAMIR after validating the total-share and reconstruction-threshold bounds. */
    /** @param user account to update */
    /** @param request configuration carrying required share counts */
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

    /** Applies MULTISIG_2FA after validating threshold and requiring a usable passkey for 3FA. */
    /** @param user account to update */
    /** @param request configuration carrying the optional factor threshold */
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

    /** Applies PASSKEY mode only when a passkey usable for the current login is linked. */
    /** @param user account to update */
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

    /** Resets advanced mode fields and restores STANDARD security defaults. */
    /** @param user account to update */
    private void applyStandard(UserDataBase user) {
        user.setAccountSecurity(AccountSecurityType.STANDARD);
        user.setShamirTotalShares(null);
        user.setShamirThreshold(null);
        user.setMultisigThreshold(2);
    }
}
