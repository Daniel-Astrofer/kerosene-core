package com.kerosene.auth.application.usecase.totp;

import org.springframework.stereotype.Component;
import com.kerosene.auth.application.service.account.TotpManagementService;
import com.kerosene.auth.dto.BackupCodesStatusDTO;
import com.kerosene.auth.dto.TotpSetupResponseDTO;

/** Exposes TOTP setup, confirmation, and disablement operations for an account. */
@Component
public class TotpOperationsUseCase {

    /** Service implementing the TOTP enrollment and lifecycle policy. */
    private final TotpManagementService totpManagementService;

    /** Creates the TOTP operations facade. */
    /** @param totpManagementService TOTP lifecycle service */
    public TotpOperationsUseCase(TotpManagementService totpManagementService) {
        this.totpManagementService = totpManagementService;
    }

    /** Begins TOTP setup and returns enrollment material. */
    /** @param userId account identifier */
    /** @return setup response containing client enrollment data */
    public TotpSetupResponseDTO setup(Long userId) {
        return totpManagementService.beginSetup(userId);
    }

    /** Confirms TOTP enrollment using a generated authenticator code. */
    /** @param userId account identifier */
    /** @param totpCode code submitted for setup verification */
    /** @return backup-code status after setup verification */
    public BackupCodesStatusDTO verify(Long userId, String totpCode) {
        return totpManagementService.verifySetup(userId, totpCode);
    }

    /** Disables TOTP for the account through the lifecycle service. */
    /** @param userId account identifier */
    public void disable(Long userId) {
        totpManagementService.disable(userId);
    }
}
