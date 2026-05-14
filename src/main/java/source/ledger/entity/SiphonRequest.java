package source.ledger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "siphon_requests", schema = "financial")
public class SiphonRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "destination_address", nullable = false, columnDefinition = "TEXT")
    private String destinationAddress;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160)
    private String idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(nullable = false)
    private LocalDateTime executableAfter;

    @Column(name = "revenue_cutoff_at", nullable = false)
    private LocalDateTime revenueCutoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SiphonRequestStatus status;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_reference", length = 255)
    private String approvalReference;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_by", length = 128)
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "blockchain_txid", length = 128)
    private String blockchainTxid;

    @Column(name = "provider_status", length = 64)
    private String providerStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "retryable", nullable = false)
    private boolean retryable;

    @Column(name = "cancelled_by", length = 128)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SiphonRequest() {}

    public SiphonRequest(BigDecimal amount) {
        this.amount = amount;
        this.requestedAt = LocalDateTime.now();
        this.executableAfter = this.requestedAt.plusHours(24);
        this.revenueCutoffAt = this.requestedAt;
        this.nextAttemptAt = this.executableAfter;
        this.status = SiphonRequestStatus.REQUESTED;
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getExecutableAfter() { return executableAfter; }
    public SiphonRequestStatus getStatus() { return status; }
    public void setStatus(SiphonRequestStatus status) { this.status = status; }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public void setExecutableAfter(LocalDateTime executableAfter) {
        this.executableAfter = executableAfter;
    }

    public LocalDateTime getRevenueCutoffAt() {
        return revenueCutoffAt;
    }

    public void setRevenueCutoffAt(LocalDateTime revenueCutoffAt) {
        this.revenueCutoffAt = revenueCutoffAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getApprovalReference() {
        return approvalReference;
    }

    public void setApprovalReference(String approvalReference) {
        this.approvalReference = approvalReference;
    }

    public LocalDateTime getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(LocalDateTime queuedAt) {
        this.queuedAt = queuedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getBlockchainTxid() {
        return blockchainTxid;
    }

    public void setBlockchainTxid(String blockchainTxid) {
        this.blockchainTxid = blockchainTxid;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
