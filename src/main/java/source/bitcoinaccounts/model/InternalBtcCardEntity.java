package source.bitcoinaccounts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "internal_btc_cards", schema = "financial", indexes = {
        @Index(name = "idx_internal_btc_cards_account", columnList = "bitcoin_account_id"),
        @Index(name = "idx_internal_btc_cards_ledger", columnList = "ledger_account_id")
})
public class InternalBtcCardEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "bitcoin_account_id", nullable = false)
    private UUID bitcoinAccountId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "permanent_address_id")
    private UUID permanentAddressId;

    @Column(name = "receiving_policy", nullable = false, length = 48)
    private String receivingPolicy = "ROTATING";

    @Column(name = "daily_limit_sats", nullable = false)
    private long dailyLimitSats;

    @Column(name = "monthly_limit_sats", nullable = false)
    private long monthlyLimitSats;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 48)
    private BitcoinAccountEnums.CardStatus status = BitcoinAccountEnums.CardStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBitcoinAccountId() {
        return bitcoinAccountId;
    }

    public void setBitcoinAccountId(UUID bitcoinAccountId) {
        this.bitcoinAccountId = bitcoinAccountId;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public void setLedgerAccountId(UUID ledgerAccountId) {
        this.ledgerAccountId = ledgerAccountId;
    }

    public UUID getPermanentAddressId() {
        return permanentAddressId;
    }

    public void setPermanentAddressId(UUID permanentAddressId) {
        this.permanentAddressId = permanentAddressId;
    }

    public String getReceivingPolicy() {
        return receivingPolicy;
    }

    public void setReceivingPolicy(String receivingPolicy) {
        this.receivingPolicy = receivingPolicy;
    }

    public long getDailyLimitSats() {
        return dailyLimitSats;
    }

    public void setDailyLimitSats(long dailyLimitSats) {
        this.dailyLimitSats = dailyLimitSats;
    }

    public long getMonthlyLimitSats() {
        return monthlyLimitSats;
    }

    public void setMonthlyLimitSats(long monthlyLimitSats) {
        this.monthlyLimitSats = monthlyLimitSats;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public BitcoinAccountEnums.CardStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.CardStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
