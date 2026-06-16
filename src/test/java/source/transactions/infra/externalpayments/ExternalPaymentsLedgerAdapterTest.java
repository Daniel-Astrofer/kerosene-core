package source.transactions.infra.externalpayments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import source.ledger.entity.LedgerEntry;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.transactions.application.externalpayments.ExternalPaymentsMath;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExternalPaymentsLedgerAdapterTest {

    private final LedgerService ledgerService = mock(LedgerService.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final LedgerTransactionHistoryRepository historyRepository = mock(LedgerTransactionHistoryRepository.class);
    private final ExternalPaymentsLedgerAdapter adapter = new ExternalPaymentsLedgerAdapter(
            ledgerService,
            ledgerEntryRepository,
            historyRepository,
            new ExternalPaymentsMath("mainnet"));

    @Test
    void updateBalanceRejectsSubSatoshiDeltasBeforeLedgerMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.updateBalance(10L, new BigDecimal("0.000000001"), "invalid"));

        verify(ledgerService, never()).updateBalance(any(), any(), any());
    }

    @Test
    void recordPlatformFeeRejectsFeeGreaterThanTransferAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.recordPlatformFee(
                        UUID.randomUUID(),
                        20L,
                        new BigDecimal("0.00001000"),
                        new BigDecimal("0.00001001")));

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void recordPlatformFeeAlwaysUsesPlatformOwnerAndZeroUserNetAmount() {
        UUID transferId = UUID.randomUUID();

        adapter.recordPlatformFee(
                transferId,
                20L,
                new BigDecimal("0.00010000"),
                new BigDecimal("0.00001000"));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry entry = captor.getValue();
        assertEquals(transferId, entry.getTxId());
        assertEquals("PLATFORM", entry.getUserId());
        assertEquals(new BigDecimal("0.00000000"), entry.getAmountNet());
        assertEquals(new BigDecimal("0.00001000"), entry.getFeeAmount());
        assertEquals("PENDING", entry.getStatus());
    }
}
