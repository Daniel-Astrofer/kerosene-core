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
@Table(name = "bitcoin_accounts", schema = "financial", indexes = {
        @Index(name = "idx_bitcoin_accounts_user", columnList = "user_id"),
        @Index(name = "idx_bitcoin_accounts_type_status", columnList = "type,status")
})
public class BitcoinAccountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 48)
    private BitcoinAccountEnums.AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "custody", nullable = false, length = 48)
    private BitcoinAccountEnums.CustodyType custody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 48)
    private BitcoinAccountEnums.AccountStatus status = BitcoinAccountEnums.AccountStatus.ACTIVE;

    @Column(name = "label", nullable = false, length = 96)
    private String label;

    @Column(name = "risk_tier", nullable = false, length = 32)
    private String riskTier = "BRONZE";

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

    public BitcoinAccountEnums.AccountType getType() {
        return type;
    }

    public void setType(BitcoinAccountEnums.AccountType type) {
        this.type = type;
    }

    public BitcoinAccountEnums.CustodyType getCustody() {
        return custody;
    }

    public void setCustody(BitcoinAccountEnums.CustodyType custody) {
        this.custody = custody;
    }

    public BitcoinAccountEnums.AccountStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.AccountStatus status) {
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRiskTier() {
        return riskTier;
    }

    public void setRiskTier(String riskTier) {
        this.riskTier = riskTier;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
