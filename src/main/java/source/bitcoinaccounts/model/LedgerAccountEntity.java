package source.bitcoinaccounts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bitcoin_ledger_accounts", schema = "financial", indexes = {
        @Index(name = "idx_bitcoin_ledger_accounts_user", columnList = "user_id"),
        @Index(name = "idx_bitcoin_ledger_accounts_account", columnList = "bitcoin_account_id")
})
public class LedgerAccountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bitcoin_account_id", nullable = false)
    private UUID bitcoinAccountId;

    @Column(name = "currency", nullable = false, length = 16)
    private String currency = "BTC";

    @Column(name = "balance_available_sats", nullable = false)
    private long balanceAvailableSats;

    @Column(name = "balance_pending_sats", nullable = false)
    private long balancePendingSats;

    @Column(name = "balance_locked_sats", nullable = false)
    private long balanceLockedSats;

    @Column(name = "balance_auto_hold_sats", nullable = false)
    private long balanceAutoHoldSats;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UUID getBitcoinAccountId() {
        return bitcoinAccountId;
    }

    public void setBitcoinAccountId(UUID bitcoinAccountId) {
        this.bitcoinAccountId = bitcoinAccountId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceAvailableSats() {
        return balanceAvailableSats;
    }

    public void setBalanceAvailableSats(long balanceAvailableSats) {
        this.balanceAvailableSats = balanceAvailableSats;
    }

    public long getBalancePendingSats() {
        return balancePendingSats;
    }

    public void setBalancePendingSats(long balancePendingSats) {
        this.balancePendingSats = balancePendingSats;
    }

    public long getBalanceLockedSats() {
        return balanceLockedSats;
    }

    public void setBalanceLockedSats(long balanceLockedSats) {
        this.balanceLockedSats = balanceLockedSats;
    }

    public long getBalanceAutoHoldSats() {
        return balanceAutoHoldSats;
    }

    public void setBalanceAutoHoldSats(long balanceAutoHoldSats) {
        this.balanceAutoHoldSats = balanceAutoHoldSats;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
