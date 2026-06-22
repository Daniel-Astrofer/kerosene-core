package source.auth.application.usecase.activation;

import org.junit.jupiter.api.Test;
import source.auth.application.service.account.AccountActivationService;
import source.auth.dto.AccountActivationStatusDTO;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivationOperationsUseCaseTest {

    private final AccountActivationService accountActivationService = mock(AccountActivationService.class);
    private final AccountActivationOperationsUseCase useCase =
            new AccountActivationOperationsUseCase(accountActivationService);

    @Test
    void getStatusDelegatesToAccountActivationService() {
        AccountActivationStatusDTO status = inactiveStatus();
        when(accountActivationService.getStatus(42L)).thenReturn(status);

        AccountActivationStatusDTO result = useCase.getStatus(42L);

        assertSame(status, result);
        verify(accountActivationService).getStatus(42L);
    }

    @Test
    void createOrReuseLinkDelegatesToAccountActivationService() {
        AccountActivationStatusDTO status = inactiveStatus();
        when(accountActivationService.createOrReuseLink(42L)).thenReturn(status);

        AccountActivationStatusDTO result = useCase.createOrReuseLink(42L);

        assertSame(status, result);
        verify(accountActivationService).createOrReuseLink(42L);
    }

    @Test
    void confirmDelegatesToAccountActivationService() {
        AccountActivationStatusDTO status = inactiveStatus();
        when(accountActivationService.confirm(42L, "link-1", "tx-1", "bc1-source"))
                .thenReturn(status);

        AccountActivationStatusDTO result = useCase.confirm(42L, "link-1", "tx-1", "bc1-source");

        assertSame(status, result);
        verify(accountActivationService).confirm(42L, "link-1", "tx-1", "bc1-source");
    }

    private AccountActivationStatusDTO inactiveStatus() {
        return new AccountActivationStatusDTO(
                false,
                false,
                true,
                BigDecimal.ZERO,
                null,
                null,
                null,
                AccountActivationStatusDTO.INBOUND_BLOCKED_MESSAGE,
                null);
    }
}
