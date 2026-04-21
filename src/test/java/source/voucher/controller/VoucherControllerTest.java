package source.voucher.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.common.dto.ApiResponse;
import source.auth.application.orchestrator.signup.FinalizeSignupOnPayment;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.transactions.service.PaymentLinkService;
import source.voucher.service.VoucherService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class VoucherControllerTest {

    @Test
    void shouldFinalizeUserOnMockOnboardingConfirm() {
        FinalizeSignupOnPayment finalizeSignupOnPayment = mock(FinalizeSignupOnPayment.class);
        when(finalizeSignupOnPayment.execute(eq("session-123"), anyString(), any())).thenReturn(true);

        VoucherController controller = new VoucherController(
                mock(VoucherService.class),
                mock(PaymentLinkService.class),
                mock(SignupStateStore.class),
                finalizeSignupOnPayment);

        ResponseEntity<ApiResponse<String>> response = controller.mockOnboardingConfirm("session-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(finalizeSignupOnPayment).execute(eq("session-123"), anyString(), any());
    }
}
