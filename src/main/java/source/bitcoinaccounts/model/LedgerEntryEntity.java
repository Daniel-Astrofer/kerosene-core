package source.bitcoinaccounts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bitcoin_ledger_entries", schema = "financial", indexes = {
        @Index(name = "idx_bitcoin_ledger_entries_account", columnList = "ledger_account_id"),
        @Index(name = "idx_bitcoin_ledger_entries_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_bitcoin_ledger_entries_source", columnList = "source_type,source_id")
})
public class LedgerEntryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private BitcoinAccountEnums.LedgerDirection direction;

    @Column(name = "amount_sats", nullable = false)
    private long amountSats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BitcoinAccountEnums.LedgerEntryStatus status;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 160)
    private String sourceId;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public void setLedgerAccountId(UUID ledgerAccountId) {
        this.ledgerAccountId = ledgerAccountId;
    }

    public BitcoinAccountEnums.LedgerDirection getDirection() {
        return direction;
    }

    public void setDirection(BitcoinAccountEnums.LedgerDirection direction) {
        this.direction = direction;
    }

    public long getAmountSats() {
        return amountSats;
    }

    public void setAmountSats(long amountSats) {
        this.amountSats = amountSats;
    }

    public BitcoinAccountEnums.LedgerEntryStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.LedgerEntryStatus status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
