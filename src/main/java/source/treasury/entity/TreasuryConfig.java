package source.treasury.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "treasury_config", schema = "financial")
public class TreasuryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_withdraw_limit", nullable = false, precision = 19, scale = 8)
    private BigDecimal maxWithdrawLimit = BigDecimal.ONE; // 1 BTC

    @Column(name = "audit_xpub")
    private String auditXpub;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TreasuryConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getMaxWithdrawLimit() { return maxWithdrawLimit; }
    public void setMaxWithdrawLimit(BigDecimal maxWithdrawLimit) { this.maxWithdrawLimit = maxWithdrawLimit; }

    public String getAuditXpub() { return auditXpub; }
    public void setAuditXpub(String auditXpub) { this.auditXpub = auditXpub; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    @PreUpdate
    public void preSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
