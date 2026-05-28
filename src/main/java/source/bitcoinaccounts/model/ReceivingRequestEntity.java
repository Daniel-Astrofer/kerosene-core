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
@Table(name = "receiving_requests", schema = "financial", indexes = {
        @Index(name = "idx_receiving_requests_card", columnList = "card_id"),
        @Index(name = "idx_receiving_requests_address", columnList = "address_id"),
        @Index(name = "idx_receiving_requests_public_code", columnList = "public_code", unique = true),
        @Index(name = "idx_receiving_requests_status_expires", columnList = "status,expires_at")
})
public class ReceivingRequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "public_code", nullable = false, unique = true, length = 48)
    private String publicCode;

    @Column(name = "amount_sats")
    private Long amountSats;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "one_time", nullable = false)
    private boolean oneTime = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 48)
    private BitcoinAccountEnums.ReceivingRequestStatus status = BitcoinAccountEnums.ReceivingRequestStatus.ACTIVE;

    @Column(name = "self_service_reason", length = 160)
    private String selfServiceReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "purge_after", nullable = false)
    private LocalDateTime purgeAfter;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (purgeAfter == null) {
            purgeAfter = now.plusHours(24);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public void setAddressId(UUID addressId) {
        this.addressId = addressId;
    }

    public String getPublicCode() {
        return publicCode;
    }

    public void setPublicCode(String publicCode) {
        this.publicCode = publicCode;
    }

    public Long getAmountSats() {
        return amountSats;
    }

    public void setAmountSats(Long amountSats) {
        this.amountSats = amountSats;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isOneTime() {
        return oneTime;
    }

    public void setOneTime(boolean oneTime) {
        this.oneTime = oneTime;
    }

    public BitcoinAccountEnums.ReceivingRequestStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.ReceivingRequestStatus status) {
        this.status = status;
    }

    public String getSelfServiceReason() {
        return selfServiceReason;
    }

    public void setSelfServiceReason(String selfServiceReason) {
        this.selfServiceReason = selfServiceReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getPurgeAfter() {
        return purgeAfter;
    }

    public void setPurgeAfter(LocalDateTime purgeAfter) {
        this.purgeAfter = purgeAfter;
    }
}
