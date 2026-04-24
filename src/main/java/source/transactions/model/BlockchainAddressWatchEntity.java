package source.transactions.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blockchain_address_watch", schema = "financial", indexes = {
        @Index(name = "idx_blockchain_watch_address_status", columnList = "address, status"),
        @Index(name = "idx_blockchain_watch_transfer", columnList = "transfer_id", unique = true),
        @Index(name = "idx_blockchain_watch_txid", columnList = "observed_txid")
})
public class BlockchainAddressWatchEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "transfer_id", nullable = false, unique = true)
    private UUID transferId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "address", nullable = false, length = 128)
    private String address;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "WATCHING";

    @Column(name = "observed_txid", length = 128)
    private String observedTxid;

    @Column(name = "observed_amount_sats")
    private Long observedAmountSats;

    @Column(name = "confirmations", nullable = false)
    private Integer confirmations = 0;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getObservedTxid() {
        return observedTxid;
    }

    public void setObservedTxid(String observedTxid) {
        this.observedTxid = observedTxid;
    }

    public Long getObservedAmountSats() {
        return observedAmountSats;
    }

    public void setObservedAmountSats(Long observedAmountSats) {
        this.observedAmountSats = observedAmountSats;
    }

    public Integer getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(Integer confirmations) {
        this.confirmations = confirmations != null ? confirmations : 0;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
