package source.auth.application.service.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.AuthExceptions;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountActivationStatusDTO;
import source.auth.model.entity.UserDataBase;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountActivationServiceTest {

    private UserServiceContract userService;
    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        userService = mock(UserServiceContract.class);
        service = new AccountActivationService(userService);
    }

    @Test
    void assertInboundEnabledShouldRejectInactiveAccount() {
        UserDataBase user = new UserDataBase();
        user.setIsActive(false);

        assertThrows(AuthExceptions.AuthValidationException.class, () -> service.assertInboundEnabled(user));
    }

    @Test
    void assertInboundEnabledShouldAllowActiveAccount() {
        UserDataBase user = new UserDataBase();
        user.setIsActive(true);

        assertDoesNotThrow(() -> service.assertInboundEnabled(user));
    }

    @Test
    void createOrReuseLinkShouldReturnBlockedStatusWithoutActivationPaymentLink() {
        UserDataBase user = new UserDataBase();
        user.setIsActive(false);

        when(userService.buscarPorId(11L)).thenReturn(Optional.of(user));

        AccountActivationStatusDTO status = service.createOrReuseLink(11L);

        assertEquals(null, status.paymentLinkId());
        assertEquals(null, status.depositAddress());
        assertEquals(null, status.paymentStatus());
        assertEquals(BigDecimal.ZERO, status.requiredAmountBtc());
        assertEquals(AccountActivationStatusDTO.INBOUND_BLOCKED_MESSAGE, status.warningMessage());
    }
}
