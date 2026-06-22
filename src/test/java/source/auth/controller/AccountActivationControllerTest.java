package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import source.auth.application.usecase.activation.AccountActivationOperationsUseCase;
import source.auth.dto.AccountActivationStatusDTO;
import source.common.dto.ApiResponse;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivationControllerTest {

    private final AccountActivationOperationsUseCase accountActivationOperationsUseCase =
            mock(AccountActivationOperationsUseCase.class);
    private final AccountActivationController controller =
            new AccountActivationController(accountActivationOperationsUseCase);

    @Test
    void getStatusDelegatesParsedUserIdAndMapsSuccessMessage() {
        Authentication authentication = authentication("42");
        AccountActivationStatusDTO status = inactiveStatus();
        when(accountActivationOperationsUseCase.getStatus(42L)).thenReturn(status);

        ResponseEntity<ApiResponse<AccountActivationStatusDTO>> response = controller.getStatus(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Activation status retrieved successfully.", response.getBody().getMessage());
        assertEquals(status, response.getBody().getData());
        verify(accountActivationOperationsUseCase).getStatus(42L);
    }

    @Test
    void createFundingLinkDelegatesParsedUserIdAndMapsSuccessMessage() {
        Authentication authentication = authentication("42");
        AccountActivationStatusDTO status = inactiveStatus();
        when(accountActivationOperationsUseCase.createOrReuseLink(42L)).thenReturn(status);

        ResponseEntity<ApiResponse<AccountActivationStatusDTO>> response =
                controller.createFundingLink(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Initial funding is prepared inside the KFE flow.", response.getBody().getMessage());
        assertEquals(status, response.getBody().getData());
        verify(accountActivationOperationsUseCase).createOrReuseLink(42L);
    }

    @Test
    void confirmDelegatesParsedUserIdAndRequestFieldsAndMapsSuccessMessage() {
        Authentication authentication = authentication("42");
        AccountActivationStatusDTO status = inactiveStatus();
        when(accountActivationOperationsUseCase.confirm(42L, "link-1", "tx-1", "bc1-source"))
                .thenReturn(status);

        ResponseEntity<ApiResponse<AccountActivationStatusDTO>> response = controller.confirm(
                "link-1",
                Map.of("txid", "tx-1", "fromAddress", "bc1-source"),
                authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Activation status retrieved successfully.", response.getBody().getMessage());
        assertEquals(status, response.getBody().getData());
        verify(accountActivationOperationsUseCase).confirm(42L, "link-1", "tx-1", "bc1-source");
    }

    private Authentication authentication(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
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
