package source.mining.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MiningHistoryPort {

    void record(MiningHistoryRecord record);

    record MiningHistoryRecord(
            Long userId,
            String senderIdentifier,
            String receiverIdentifier,
            String transactionType,
            BigDecimal amount,
            String status,
            String blockchainTxid,
            String context,
            LocalDateTime createdAt) {
    }
}
