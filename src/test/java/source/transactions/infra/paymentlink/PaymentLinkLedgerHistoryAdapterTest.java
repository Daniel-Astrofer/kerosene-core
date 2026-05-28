package source.transactions.infra.paymentlink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.transactions.dto.PaymentLinkDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentLinkLedgerHistoryAdapterTest {

    @Mock
    private LedgerTransactionHistoryRepository historyRepository;

    @Test
    void recordCreatedPersistsPendingSenderIdentifier() {
        PaymentLinkLedgerHistoryAdapter adapter = new PaymentLinkLedgerHistoryAdapter(historyRepository);
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay_123456789abc");
        paymentLink.setUserId(7L);
        paymentLink.setAmountBtc(new BigDecimal("0.00133862"));
        paymentLink.setDepositAddress("1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP");
        paymentLink.setCreatedAt(LocalDateTime.of(2026, 4, 16, 10, 15));

        adapter.recordCreated(paymentLink);

        ArgumentCaptor<LedgerTransactionHistory> historyCaptor =
                ArgumentCaptor.forClass(LedgerTransactionHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        LedgerTransactionHistory history = historyCaptor.getValue();
        assertEquals("BITCOIN_NETWORK_PENDING", history.getSenderIdentifier());
        assertEquals("1A1z7agoat7F9gq5TFGakzrx6R5vbR2rAP", history.getReceiverIdentifier());
        assertEquals("DEPOSIT", history.getTransactionType());
        assertEquals("PENDING", history.getStatus());
    }
}
