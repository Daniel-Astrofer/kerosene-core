package source.transactions.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_transactions", schema = "financial", indexes = {
        @Index(name = "idx_processed_transactions_txid", columnList = "txid", unique = true)
})
public class ProcessedTransactionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "txid", nullable = false, unique = true, length = 128)
    private String txid;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    protected ProcessedTransactionEntity() {
    }

    public ProcessedTransactionEntity(String txid, String source) {
        this.txid = txid;
        this.source = source;
    }

    public UUID getId() {
        return id;
    }

    public String getTxid() {
        return txid;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
