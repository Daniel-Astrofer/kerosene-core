package source.voucher.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.common.dto.ApiResponse;
import source.auth.application.orchestrator.signup.SignupUseCase;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.util.DevBalanceInjector;
import source.transactions.service.PaymentLinkService;
import source.voucher.service.VoucherService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VoucherControllerTest {

    @Test
    void shouldKeepMockOnboardingEndpointDisabledByDefault() {
        SignupUseCase signupUseCase = mock(SignupUseCase.class);
        VoucherController controller = new VoucherController(
                mock(VoucherService.class),
                mock(PaymentLinkService.class),
                mock(source.auth.application.infra.persistance.redis.contracts.RedisContract.class),
                signupUseCase,
                mock(UserServiceContract.class),
                mock(DevBalanceInjector.class),
                false);

        ResponseEntity<ApiResponse<String>> response = controller.mockOnboardingConfirm("session-123");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("MOCK_DISABLED", response.getBody().getErrorCode());
        verifyNoInteractions(signupUseCase);
    }
}
