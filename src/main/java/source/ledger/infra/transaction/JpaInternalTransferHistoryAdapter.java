package source.ledger.infra.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.InternalTransferHistoryPort;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.util.UUID;

@Component
public class JpaInternalTransferHistoryAdapter implements InternalTransferHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(JpaInternalTransferHistoryAdapter.class);

    private final LedgerTransactionHistoryRepository historyRepository;

    public JpaInternalTransferHistoryAdapter(LedgerTransactionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Override
    public void recordInternalTransfer(InternalTransferRecord record) {
        try {
            LedgerTransactionHistory senderHistory = new LedgerTransactionHistory();
            senderHistory.setId(UUID.randomUUID());
            senderHistory.setAmount(record.amount());
            senderHistory.setCreatedAt(record.createdAt());
            senderHistory.setContext(record.context());
            senderHistory.setSenderUserId(record.senderUserId());
            senderHistory.setSenderIdentifier(record.senderIdentifier());
            senderHistory.setReceiverUserId(record.receiverUserId());
            senderHistory.setReceiverIdentifier(record.receiverIdentifier());
            senderHistory.setTransactionType("INTERNAL");
            senderHistory.setStatus("CONCLUDED");
            historyRepository.save(senderHistory);

            LedgerTransactionHistory receiverHistory = new LedgerTransactionHistory();
            receiverHistory.setId(UUID.randomUUID());
            receiverHistory.setAmount(record.amount());
            receiverHistory.setCreatedAt(record.createdAt());
            receiverHistory.setContext(record.context());
            receiverHistory.setSenderUserId(record.senderUserId());
            receiverHistory.setSenderIdentifier(record.senderIdentifier());
            receiverHistory.setReceiverUserId(record.receiverUserId());
            receiverHistory.setReceiverIdentifier(record.receiverIdentifier());
            receiverHistory.setTransactionType("INTERNAL");
            receiverHistory.setStatus("CONCLUDED");
            historyRepository.save(receiverHistory);
        } catch (Exception exception) {
            log.error("Failed to save split transaction history: {}", exception.getMessage());
        }
    }
}
