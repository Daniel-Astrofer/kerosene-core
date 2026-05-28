package source.transactions.infra.paymentlink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.transactions.application.paymentlink.PaymentLinkHistoryPort;
import source.transactions.dto.PaymentLinkDTO;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PaymentLinkLedgerHistoryAdapter implements PaymentLinkHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkLedgerHistoryAdapter.class);

    private final LedgerTransactionHistoryRepository historyRepository;

    public PaymentLinkLedgerHistoryAdapter(LedgerTransactionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Override
    public void recordCreated(PaymentLinkDTO paymentLink) {
        try {
            LedgerTransactionHistory history = new LedgerTransactionHistory();
            history.setId(UUID.nameUUIDFromBytes(paymentLink.getId().getBytes(StandardCharsets.UTF_8)));
            history.setAmount(paymentLink.getAmountBtc().abs());
            history.setCreatedAt(paymentLink.getCreatedAt());
            history.setContext("On-Chain Deposit via Link: " + paymentLink.getId());
            history.setReceiverUserId(paymentLink.getUserId());
            history.setReceiverIdentifier(paymentLink.getDepositAddress());
            history.setSenderIdentifier("BITCOIN_NETWORK_PENDING");
            history.setTransactionType("DEPOSIT");
            history.setStatus("PENDING");
            historyRepository.save(history);
        } catch (Exception ex) {
            log.warn("Failed to save deposit history for payment link {}", paymentLink.getId(), ex);
        }
    }

    @Override
    public void markConfirmed(PaymentLinkDTO paymentLink, String fromAddress) {
        try {
            UUID historyId = UUID.nameUUIDFromBytes(paymentLink.getId().getBytes(StandardCharsets.UTF_8));
            historyRepository.findById(historyId).ifPresent(history -> {
                history.setStatus("CONCLUDED");
                history.setBlockchainTxid(paymentLink.getTxid());
                history.setSenderIdentifier(fromAddress != null ? fromAddress : "Bitcoin Network");
                if (paymentLink.getGrossAmountBtc() != null
                        && paymentLink.getDepositFeeBtc() != null
                        && paymentLink.getNetAmountBtc() != null) {
                    history.setContext("On-Chain Deposit via Link: " + paymentLink.getId()
                            + " | gross=" + paymentLink.getGrossAmountBtc().toPlainString()
                            + " BTC | fee=" + paymentLink.getDepositFeeBtc().toPlainString()
                            + " BTC | net=" + paymentLink.getNetAmountBtc().toPlainString() + " BTC");
                }
                historyRepository.save(history);
            });
        } catch (Exception ex) {
            log.warn("Failed to update history for payment link {}", paymentLink.getId(), ex);
        }
    }
}
