package source.ledger.application.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface InternalTransferHistoryPort {

    void recordInternalTransfer(InternalTransferRecord record);

    record InternalTransferRecord(
            BigDecimal amount,
            LocalDateTime createdAt,
            String context,
            Long senderUserId,
            String senderIdentifier,
            Long receiverUserId,
            String receiverIdentifier) {
    }
}
