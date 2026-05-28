package source.mining.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mining_rig_offers", schema = "financial", indexes = {
        @Index(name = "idx_mining_rig_offers_active", columnList = "active"),
        @Index(name = "idx_mining_rig_offers_algorithm", columnList = "algorithm")
})
public class MiningRigOfferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rig_code", nullable = false, unique = true, length = 64)
    private String rigCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "algorithm", nullable = false, length = 64)
    private String algorithm;

    @Column(name = "hash_unit", nullable = false, length = 16)
    private String hashUnit;

    @Column(name = "price_per_unit_day_btc", nullable = false, precision = 19, scale = 8)
    private BigDecimal pricePerUnitDayBtc;

    @Column(name = "projected_btc_yield_per_unit_day", nullable = false, precision = 19, scale = 8)
    private BigDecimal projectedBtcYieldPerUnitDay;

    @Column(name = "projected_yield_multiplier", nullable = false, precision = 10, scale = 8)
    private BigDecimal projectedYieldMultiplier;

    @Column(name = "available_hashrate", nullable = false, precision = 19, scale = 8)
    private BigDecimal availableHashrate;

    @Column(name = "min_rental_hours", nullable = false)
    private Integer minRentalHours;

    @Column(name = "max_rental_hours", nullable = false)
    private Integer maxRentalHours;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRigCode() {
        return rigCode;
    }

    public void setRigCode(String rigCode) {
        this.rigCode = rigCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getHashUnit() {
        return hashUnit;
    }

    public void setHashUnit(String hashUnit) {
        this.hashUnit = hashUnit;
    }

    public BigDecimal getPricePerUnitDayBtc() {
        return pricePerUnitDayBtc;
    }

    public void setPricePerUnitDayBtc(BigDecimal pricePerUnitDayBtc) {
        this.pricePerUnitDayBtc = pricePerUnitDayBtc;
    }

    public BigDecimal getProjectedBtcYieldPerUnitDay() {
        return projectedBtcYieldPerUnitDay;
    }

    public void setProjectedBtcYieldPerUnitDay(BigDecimal projectedBtcYieldPerUnitDay) {
        this.projectedBtcYieldPerUnitDay = projectedBtcYieldPerUnitDay;
    }

    public BigDecimal getProjectedYieldMultiplier() {
        return projectedYieldMultiplier;
    }

    public void setProjectedYieldMultiplier(BigDecimal projectedYieldMultiplier) {
        this.projectedYieldMultiplier = projectedYieldMultiplier;
    }

    public BigDecimal getAvailableHashrate() {
        return availableHashrate;
    }

    public void setAvailableHashrate(BigDecimal availableHashrate) {
        this.availableHashrate = availableHashrate;
    }

    public Integer getMinRentalHours() {
        return minRentalHours;
    }

    public void setMinRentalHours(Integer minRentalHours) {
        this.minRentalHours = minRentalHours;
    }

    public Integer getMaxRentalHours() {
        return maxRentalHours;
    }

    public void setMaxRentalHours(Integer maxRentalHours) {
        this.maxRentalHours = maxRentalHours;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
