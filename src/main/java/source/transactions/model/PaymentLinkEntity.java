package source.transactions.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_links", schema = "financial", indexes = {
        @Index(name = "idx_payment_links_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_payment_links_session", columnList = "session_id"),
        @Index(name = "idx_payment_links_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_payment_links_deposit_address", columnList = "deposit_address"),
        @Index(name = "idx_payment_links_txid", columnList = "txid")
})
public class PaymentLinkEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "amount_btc", precision = 19, scale = 8, nullable = false)
    private BigDecimal amountBtc;

    @Column(name = "gross_amount_btc", precision = 19, scale = 8)
    private BigDecimal grossAmountBtc;

    @Column(name = "deposit_fee_btc", precision = 19, scale = 8)
    private BigDecimal depositFeeBtc;

    @Column(name = "net_amount_btc", precision = 19, scale = 8)
    private BigDecimal netAmountBtc;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "deposit_address", nullable = false, length = 128)
    private String depositAddress;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility;

    @Column(name = "confirmation_mode", nullable = false, length = 32)
    private String confirmationMode;

    @Column(name = "amount_locked", nullable = false)
    private Boolean amountLocked = true;

    @Column(name = "reference_label", length = 64)
    private String referenceLabel;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "txid", length = 128)
    private String txid;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public BigDecimal getAmountBtc() {
        return amountBtc;
    }

    public void setAmountBtc(BigDecimal amountBtc) {
        this.amountBtc = amountBtc;
    }

    public BigDecimal getGrossAmountBtc() {
        return grossAmountBtc;
    }

    public void setGrossAmountBtc(BigDecimal grossAmountBtc) {
        this.grossAmountBtc = grossAmountBtc;
    }

    public BigDecimal getDepositFeeBtc() {
        return depositFeeBtc;
    }

    public void setDepositFeeBtc(BigDecimal depositFeeBtc) {
        this.depositFeeBtc = depositFeeBtc;
    }

    public BigDecimal getNetAmountBtc() {
        return netAmountBtc;
    }

    public void setNetAmountBtc(BigDecimal netAmountBtc) {
        this.netAmountBtc = netAmountBtc;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDepositAddress() {
        return depositAddress;
    }

    public void setDepositAddress(String depositAddress) {
        this.depositAddress = depositAddress;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getConfirmationMode() {
        return confirmationMode;
    }

    public void setConfirmationMode(String confirmationMode) {
        this.confirmationMode = confirmationMode;
    }

    public Boolean getAmountLocked() {
        return amountLocked;
    }

    public void setAmountLocked(Boolean amountLocked) {
        this.amountLocked = amountLocked;
    }

    public String getReferenceLabel() {
        return referenceLabel;
    }

    public void setReferenceLabel(String referenceLabel) {
        this.referenceLabel = referenceLabel;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
