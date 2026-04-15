package source.transactions.application.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionHistoryPort {

    void recordUnsignedTransaction(UnsignedTransactionRecord record);

    void recordBroadcast(BroadcastRecord record);

    record UnsignedTransactionRecord(
            String fromAddress,
            String toAddress,
            BigDecimal amount,
            LocalDateTime createdAt) {
    }

    record BroadcastRecord(
            String txid,
            Long userId,
            String toAddress,
            BigDecimal amount,
            String message,
            LocalDateTime createdAt) {
    }
}
