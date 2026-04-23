package source.voucher.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.common.dto.ApiResponse;
import source.voucher.service.VoucherService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoucherControllerTest {

    @Test
    void shouldRequestVoucherWithoutOnboardingSignupFlow() {
        VoucherService voucherService = mock(VoucherService.class);
        when(voucherService.requestVoucher()).thenReturn(new VoucherService.VoucherRequestData(
                "bc1qvoucher",
                22000L,
                "pending-1"));

        VoucherController controller = new VoucherController(voucherService);

        ResponseEntity<ApiResponse<java.util.Map<String, Object>>> response = controller.requestVoucher();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bc1qvoucher", response.getBody().getData().get("depositAddress"));
        assertEquals(22000L, response.getBody().getData().get("amountSats"));
        assertEquals("pending-1", response.getBody().getData().get("pendingVoucherId"));
    }
}
