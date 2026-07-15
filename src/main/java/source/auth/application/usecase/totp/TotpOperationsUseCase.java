package source.auth.application.usecase.totp;

import org.springframework.stereotype.Component;
import source.auth.application.service.account.TotpManagementService;
import source.auth.dto.BackupCodesStatusDTO;
import source.auth.dto.TotpSetupResponseDTO;

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
