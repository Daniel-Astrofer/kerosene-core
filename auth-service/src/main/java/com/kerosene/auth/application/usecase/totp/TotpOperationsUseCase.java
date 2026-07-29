package com.kerosene.auth.application.usecase.totp;

import org.springframework.stereotype.Component;
import com.kerosene.auth.application.service.account.TotpManagementService;
import com.kerosene.auth.dto.BackupCodesStatusDTO;
import com.kerosene.auth.dto.TotpSetupResponseDTO;

@Component
public class TotpOperationsUseCase {

    private final TotpManagementService totpManagementService;

    public TotpOperationsUseCase(TotpManagementService totpManagementService) {
        this.totpManagementService = totpManagementService;
    }

    public TotpSetupResponseDTO setup(Long userId) {
        return totpManagementService.beginSetup(userId);
    }

    public BackupCodesStatusDTO verify(Long userId, String totpCode) {
        return totpManagementService.verifySetup(userId, totpCode);
    }

    public void disable(Long userId) {
        totpManagementService.disable(userId);
    }
}
