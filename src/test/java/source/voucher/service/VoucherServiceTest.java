package source.voucher.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.Voucher;
import source.voucher.repository.VoucherRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VoucherServiceTest {

    private VoucherService voucherService;

    @Mock
    private VoucherRepository repository;

    @Mock
    private UserServiceContract userService;

    @BeforeEach
    public void setup() {
        voucherService = new VoucherService(
                repository,
                userService,
                "1A1z7agoat7F9gq5TF..."
        );
    }

    @Test
    public void testConfirmPaymentSuccess() {
        UUID voucherId = UUID.randomUUID();
        String txid = "real_txid_64_chars_long_placeholder_12345678901234567890123456";

        Voucher pendingVoucher = new Voucher();
        pendingVoucher.setId(voucherId);
        pendingVoucher.setStatus(Voucher.VoucherStatus.PENDING);
        pendingVoucher.setValueSats(22000);

        when(repository.findById(voucherId)).thenReturn(Optional.of(pendingVoucher));
        when(repository.findByTxid(txid)).thenReturn(Optional.empty());

        String code = voucherService.confirmPayment(voucherId.toString(), txid);

        assertNotNull(code);
        assertEquals(Voucher.VoucherStatus.PAID, pendingVoucher.getStatus());
        assertEquals(txid, pendingVoucher.getTxid());
        verify(repository).save(pendingVoucher);
    }

    @Test
    public void testConfirmPaymentFailsWhenPendingVoucherDoesNotExist() {
        UUID voucherId = UUID.randomUUID();
        String txid = "tx_without_pending_voucher";
        when(repository.findById(voucherId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            voucherService.confirmPayment(voucherId.toString(), txid);
        });
    }

    @Test
    public void testConfirmPaymentReturnsExistingCodeWhenVoucherAlreadyPaid() {
        UUID voucherId = UUID.randomUUID();
        String txid = "reused_txid";

        Voucher existingVoucher = new Voucher();
        existingVoucher.setId(voucherId);
        existingVoucher.setStatus(Voucher.VoucherStatus.PAID);
        existingVoucher.setCode("ABC123");

        when(repository.findByTxid(txid)).thenReturn(Optional.of(existingVoucher));

        assertEquals("ABC123", voucherService.confirmPayment(voucherId.toString(), txid));
    }
}
