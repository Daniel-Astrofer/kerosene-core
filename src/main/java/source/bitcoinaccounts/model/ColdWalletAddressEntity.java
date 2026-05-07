package source.bitcoinaccounts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cold_wallet_addresses", schema = "financial", indexes = {
        @Index(name = "idx_cold_wallet_addresses_wallet", columnList = "cold_wallet_id"),
        @Index(name = "idx_cold_wallet_addresses_address", columnList = "address", unique = true)
})
public class ColdWalletAddressEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "cold_wallet_id", nullable = false)
    private UUID coldWalletId;

    @Column(name = "address", nullable = false, length = 128)
    private String address;

    @Column(name = "derivation_index", nullable = false)
    private int derivationIndex;

    @Column(name = "is_change", nullable = false)
    private boolean change;

    @Column(name = "observed_balance_sats", nullable = false)
    private long observedBalanceSats;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

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

    public UUID getColdWalletId() {
        return coldWalletId;
    }

    public void setColdWalletId(UUID coldWalletId) {
        this.coldWalletId = coldWalletId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getDerivationIndex() {
        return derivationIndex;
    }

    public void setDerivationIndex(int derivationIndex) {
        this.derivationIndex = derivationIndex;
    }

    public boolean isChange() {
        return change;
    }

    public void setChange(boolean change) {
        this.change = change;
    }

    public long getObservedBalanceSats() {
        return observedBalanceSats;
    }

    public void setObservedBalanceSats(long observedBalanceSats) {
        this.observedBalanceSats = observedBalanceSats;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
