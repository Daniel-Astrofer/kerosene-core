package source.ledger.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface LedgerSyncEventView {
    UUID getId();

    String getTransactionType();

    BigDecimal getAmount();

    String getStatus();

    Long getSenderUserId();

    Long getReceiverUserId();

    BigDecimal getNetworkFee();

    String getBlockchainTxid();

    LocalDateTime getCreatedAt();

    Integer getConfirmations();
}
