package source.transactions.application.externalpayments;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ExternalPaymentsLedgerPort {

    void ensureBalance(Long walletId, BigDecimal requiredAmount);

    void updateBalance(Long walletId, BigDecimal amount, String context);

    void recordPlatformFee(UUID transferId, Long userId, BigDecimal totalDebited, BigDecimal platformFee);

    void recordHistory(HistoryRecord historyRecord);

    record HistoryRecord(
            Long userId,
            String senderIdentifier,
            String receiverIdentifier,
            String transactionType,
            BigDecimal amount,
            BigDecimal networkFee,
            String status,
            String blockchainTxid,
            String context,
            LocalDateTime createdAt) {
    }
}
