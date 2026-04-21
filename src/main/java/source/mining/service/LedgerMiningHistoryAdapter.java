package source.mining.service;

import org.springframework.stereotype.Component;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class LedgerMiningHistoryAdapter implements MiningHistoryPort {

    private final LedgerTransactionHistoryRepository historyRepository;

    public LedgerMiningHistoryAdapter(LedgerTransactionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Override
    public void record(MiningHistoryRecord record) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setSenderUserId(record.userId());
        history.setReceiverUserId(record.userId());
        history.setSenderIdentifier(record.senderIdentifier());
        history.setReceiverIdentifier(record.receiverIdentifier());
        history.setTransactionType(record.transactionType());
        history.setAmount(normalize(record.amount()));
        history.setStatus(record.status());
        history.setBlockchainTxid(record.blockchainTxid());
        history.setContext(record.context());
        history.setCreatedAt(record.createdAt() != null ? record.createdAt() : LocalDateTime.now());
        historyRepository.save(history);
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
