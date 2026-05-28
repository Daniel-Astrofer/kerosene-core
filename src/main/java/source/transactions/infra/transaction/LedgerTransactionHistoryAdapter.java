package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.transactions.application.transaction.TransactionHistoryPort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class LedgerTransactionHistoryAdapter implements TransactionHistoryPort {

    private final LedgerTransactionHistoryRepository historyRepository;

    public LedgerTransactionHistoryAdapter(LedgerTransactionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Override
    public void recordUnsignedTransaction(UnsignedTransactionRecord record) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setAmount(record.amount() != null ? record.amount() : BigDecimal.ZERO);
        history.setCreatedAt(record.createdAt() != null ? record.createdAt() : LocalDateTime.now());
        history.setContext("Unsigned Transaction created for address: " + safeText(record.toAddress()));
        history.setSenderIdentifier(safeText(record.fromAddress()));
        history.setReceiverIdentifier(safeText(record.toAddress()));
        history.setTransactionType("EXTERNAL_WITHDRAWAL");
        history.setStatus("PENDING");
        historyRepository.save(history);
    }

    @Override
    public void recordBroadcast(BroadcastRecord record) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setAmount(record.amount() != null ? record.amount() : BigDecimal.ZERO);
        history.setCreatedAt(record.createdAt() != null ? record.createdAt() : LocalDateTime.now());
        history.setContext("Broadcast Transaction: " + safeText(record.message(), "Outgoing transaction"));
        history.setSenderUserId(record.userId());
        history.setSenderIdentifier(record.userId() != null ? String.valueOf(record.userId()) : "UNKNOWN");
        history.setReceiverIdentifier(safeText(record.toAddress()));
        history.setBlockchainTxid(record.txid());
        history.setTransactionType("EXTERNAL_WITHDRAWAL");
        history.setStatus("PENDING");
        historyRepository.save(history);
    }

    private String safeText(String value) {
        return safeText(value, "UNKNOWN");
    }

    private String safeText(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
