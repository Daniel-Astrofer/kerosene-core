package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import source.auth.application.usecase.security.GetAccountSecurityStatusUseCase;
import source.auth.dto.AccountSecurityStatusDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.common.dto.ApiResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityStatusControllerTest {

    private final GetAccountSecurityStatusUseCase getAccountSecurityStatusUseCase =
            mock(GetAccountSecurityStatusUseCase.class);
    private final AccountSecurityStatusController controller =
            new AccountSecurityStatusController(getAccountSecurityStatusUseCase);

    @Test
    void getStatusDelegatesParsedUserIdAndMapsSuccessMessage() {
        Authentication authentication = authentication("42");
        AccountSecurityStatusDTO status = status();
        when(getAccountSecurityStatusUseCase.execute(42L)).thenReturn(status);

        ResponseEntity<ApiResponse<AccountSecurityStatusDTO>> response = controller.getStatus(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Account security status retrieved successfully.", response.getBody().getMessage());
        assertEquals(status, response.getBody().getData());
        verify(getAccountSecurityStatusUseCase).execute(42L);
    }

    private Authentication authentication(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }

    private AccountSecurityStatusDTO status() {
        PasskeyInventoryDTO passkeys =
                new PasskeyInventoryDTO(true, true, false, "localhost", "localhost", List.of());
        return new AccountSecurityStatusDTO(true, true, true, 2, false, null, true, true, passkeys);
    }
}
