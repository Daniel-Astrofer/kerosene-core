package source.voucher.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.Voucher;
import source.transactions.infra.BlockchainClient;
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

    @Mock
    private BlockchainClient blockchainClient;

    @Mock
    private source.treasury.service.RevenueCollector revenueCollector;

    @BeforeEach
    public void setup() {
        // Default: 1 confirmation
        lenient().when(blockchainClient.getTransactionConfirmations(anyString())).thenReturn(1);

        voucherService = new VoucherService(
                repository,
                userService,
                blockchainClient,
                revenueCollector,
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
    public void testConfirmPaymentFailsNoConfirmations() {
        UUID voucherId = UUID.randomUUID();
        String txid = "tx_without_confirmations";

        Voucher pendingVoucher = new Voucher();
        pendingVoucher.setId(voucherId);
        pendingVoucher.setStatus(Voucher.VoucherStatus.PENDING);

        when(repository.findById(voucherId)).thenReturn(Optional.of(pendingVoucher));
        when(blockchainClient.getTransactionConfirmations(txid)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> {
            voucherService.confirmPayment(voucherId.toString(), txid);
        });
    }

    @Test
    public void testConfirmPaymentFailsMockTx() {
        // Now mock_tx should fail because it won't be found on-chain (confirmations=-1)
        UUID voucherId = UUID.randomUUID();
        String txid = "mock_tx_123";

        Voucher pendingVoucher = new Voucher();
        pendingVoucher.setId(voucherId);
        pendingVoucher.setStatus(Voucher.VoucherStatus.PENDING);

        when(repository.findById(voucherId)).thenReturn(Optional.of(pendingVoucher));
        when(blockchainClient.getTransactionConfirmations(txid)).thenReturn(-1);

        assertThrows(IllegalStateException.class, () -> {
            voucherService.confirmPayment(voucherId.toString(), txid);
        });
    }
}
