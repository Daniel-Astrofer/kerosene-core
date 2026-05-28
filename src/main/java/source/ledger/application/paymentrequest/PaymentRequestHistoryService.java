package source.ledger.application.paymentrequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentRequestHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestHistoryService.class);

    private final LedgerTransactionHistoryRepository historyRepository;

    public PaymentRequestHistoryService(LedgerTransactionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void recordCreated(InternalPaymentRequestDTO request) {
        try {
            LedgerTransactionHistory history = new LedgerTransactionHistory();
            history.setId(UUID.fromString(request.getId()));
            history.setAmount(request.getAmount().abs());
            history.setCreatedAt(LocalDateTime.now());
            history.setContext("Internal Payment Request: " + request.getId());
            history.setReceiverUserId(request.getRequesterUserId());
            history.setReceiverIdentifier(request.getReceiverWalletName());
            history.setSenderIdentifier("PAYER_PENDING");
            history.setTransactionType("PAYMENT_LINK");
            history.setStatus("PENDING");
            historyRepository.save(history);
        } catch (Exception exception) {
            log.warn("Failed to save payment request history: {}", exception.getMessage());
        }
    }

    public void markAsConcluded(String linkId) {
        try {
            historyRepository.updateStatus(UUID.fromString(linkId), "CONCLUDED");
        } catch (Exception exception) {
            log.warn("Failed to update payment request history status for linkId={}: {}", linkId, exception.getMessage());
        }
    }
}
