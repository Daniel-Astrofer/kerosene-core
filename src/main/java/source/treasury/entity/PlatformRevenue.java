package source.treasury.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_revenue", schema = "financial")
@EntityListeners(HmacIntegrityListener.class)
public class PlatformRevenue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "accumulated_profit", nullable = false, precision = 19, scale = 8)
    private BigDecimal accumulatedProfit = BigDecimal.ZERO;

    @Column(name = "hmac_sha256", nullable = false)
    private String hmacSha256;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PlatformRevenue() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAccumulatedProfit() {
        return accumulatedProfit;
    }

    public void setAccumulatedProfit(BigDecimal accumulatedProfit) {
        this.accumulatedProfit = accumulatedProfit;
    }

    public String getHmacSha256() {
        return hmacSha256;
    }

    public void setHmacSha256(String hmacSha256) {
        this.hmacSha256 = hmacSha256;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
