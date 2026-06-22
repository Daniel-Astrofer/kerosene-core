package source.auth.application.usecase.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.service.account.AccountSecurityStatusService;
import source.auth.dto.AccountSecurityStatusDTO;
import source.auth.dto.PasskeyInventoryDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAccountSecurityStatusUseCaseTest {

    private AccountSecurityStatusService accountSecurityStatusService;
    private GetAccountSecurityStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        accountSecurityStatusService = mock(AccountSecurityStatusService.class);
        useCase = new GetAccountSecurityStatusUseCase(accountSecurityStatusService);
    }

    @Test
    void executeReturnsStatusFromService() {
        AccountSecurityStatusDTO status = status();
        when(accountSecurityStatusService.getStatus(42L)).thenReturn(status);

        AccountSecurityStatusDTO result = useCase.execute(42L);

        assertSame(status, result);
        verify(accountSecurityStatusService).getStatus(42L);
    }

    private AccountSecurityStatusDTO status() {
        PasskeyInventoryDTO passkeys =
                new PasskeyInventoryDTO(true, true, false, "localhost", "localhost", List.of());
        return new AccountSecurityStatusDTO(true, true, true, 2, false, null, true, true, passkeys);
    }
}
