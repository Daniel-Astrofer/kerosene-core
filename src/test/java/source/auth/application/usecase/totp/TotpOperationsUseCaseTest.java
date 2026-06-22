package source.auth.application.usecase.totp;

import org.junit.jupiter.api.Test;
import source.auth.application.service.account.TotpManagementService;
import source.auth.dto.BackupCodesStatusDTO;
import source.auth.dto.TotpSetupResponseDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpOperationsUseCaseTest {

    private final TotpManagementService totpManagementService = mock(TotpManagementService.class);
    private final TotpOperationsUseCase useCase = new TotpOperationsUseCase(totpManagementService);

    @Test
    void setupDelegatesToTotpManagementService() {
        TotpSetupResponseDTO setupResponse = new TotpSetupResponseDTO("otpauth://totp/Kerosene:user", "secret");
        when(totpManagementService.beginSetup(42L)).thenReturn(setupResponse);

        TotpSetupResponseDTO result = useCase.setup(42L);

        assertSame(setupResponse, result);
        verify(totpManagementService).beginSetup(42L);
    }

    @Test
    void verifyDelegatesToTotpManagementService() {
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 10, List.of("12345678"));
        when(totpManagementService.verifySetup(42L, "123456")).thenReturn(status);

        BackupCodesStatusDTO result = useCase.verify(42L, "123456");

        assertSame(status, result);
        verify(totpManagementService).verifySetup(42L, "123456");
    }

    @Test
    void disableDelegatesToTotpManagementService() {
        useCase.disable(42L);

        verify(totpManagementService).disable(42L);
    }
}
