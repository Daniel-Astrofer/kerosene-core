package source.mining.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mining_allocations", schema = "financial", indexes = {
        @Index(name = "idx_mining_allocations_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_mining_allocations_status", columnList = "status"),
        @Index(name = "idx_mining_allocations_wallet", columnList = "wallet_id")
})
public class MiningAllocationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "rig_id", nullable = false)
    private Long rigId;

    @Column(name = "wallet_name_snapshot", nullable = false)
    private String walletNameSnapshot;

    @Column(name = "rig_name_snapshot", nullable = false)
    private String rigNameSnapshot;

    @Column(name = "algorithm", nullable = false)
    private String algorithm;

    @Column(name = "hash_unit", nullable = false)
    private String hashUnit;

    @Column(name = "allocated_hashrate", nullable = false, precision = 19, scale = 8)
    private BigDecimal allocatedHashrate;

    @Column(name = "duration_hours", nullable = false)
    private Integer durationHours;

    @Column(name = "rental_cost_btc", nullable = false, precision = 19, scale = 8)
    private BigDecimal rentalCostBtc;

    @Column(name = "projected_gross_yield_btc", nullable = false, precision = 19, scale = 8)
    private BigDecimal projectedGrossYieldBtc;

    @Column(name = "projected_net_yield_btc", nullable = false, precision = 19, scale = 8)
    private BigDecimal projectedNetYieldBtc;

    @Column(name = "payout_address", columnDefinition = "TEXT")
    private String payoutAddress;

    @Column(name = "pool_url", columnDefinition = "TEXT")
    private String poolUrl;

    @Column(name = "worker_name")
    private String workerName;

    @Column(name = "provider_rental_reference")
    private String providerRentalReference;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "refunded_amount_btc", precision = 19, scale = 8)
    private BigDecimal refundedAmountBtc;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public Long getRigId() {
        return rigId;
    }

    public void setRigId(Long rigId) {
        this.rigId = rigId;
    }

    public String getWalletNameSnapshot() {
        return walletNameSnapshot;
    }

    public void setWalletNameSnapshot(String walletNameSnapshot) {
        this.walletNameSnapshot = walletNameSnapshot;
    }

    public String getRigNameSnapshot() {
        return rigNameSnapshot;
    }

    public void setRigNameSnapshot(String rigNameSnapshot) {
        this.rigNameSnapshot = rigNameSnapshot;
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

    public BigDecimal getAllocatedHashrate() {
        return allocatedHashrate;
    }

    public void setAllocatedHashrate(BigDecimal allocatedHashrate) {
        this.allocatedHashrate = allocatedHashrate;
    }

    public Integer getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(Integer durationHours) {
        this.durationHours = durationHours;
    }

    public BigDecimal getRentalCostBtc() {
        return rentalCostBtc;
    }

    public void setRentalCostBtc(BigDecimal rentalCostBtc) {
        this.rentalCostBtc = rentalCostBtc;
    }

    public BigDecimal getProjectedGrossYieldBtc() {
        return projectedGrossYieldBtc;
    }

    public void setProjectedGrossYieldBtc(BigDecimal projectedGrossYieldBtc) {
        this.projectedGrossYieldBtc = projectedGrossYieldBtc;
    }

    public BigDecimal getProjectedNetYieldBtc() {
        return projectedNetYieldBtc;
    }

    public void setProjectedNetYieldBtc(BigDecimal projectedNetYieldBtc) {
        this.projectedNetYieldBtc = projectedNetYieldBtc;
    }

    public String getPayoutAddress() {
        return payoutAddress;
    }

    public void setPayoutAddress(String payoutAddress) {
        this.payoutAddress = payoutAddress;
    }

    public String getPoolUrl() {
        return poolUrl;
    }

    public void setPoolUrl(String poolUrl) {
        this.poolUrl = poolUrl;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getProviderRentalReference() {
        return providerRentalReference;
    }

    public void setProviderRentalReference(String providerRentalReference) {
        this.providerRentalReference = providerRentalReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getRefundedAmountBtc() {
        return refundedAmountBtc;
    }

    public void setRefundedAmountBtc(BigDecimal refundedAmountBtc) {
        this.refundedAmountBtc = refundedAmountBtc;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
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
