package source.transactions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class PaymentLinkDTO {

    private String id;
    private Long userId; // Can be null if tied to an onboarding session
    private String sessionId; // Used for onboarding before a User is persisted
    private BigDecimal amountBtc;
    private BigDecimal grossAmountBtc;
    private BigDecimal depositFeeBtc;
    private BigDecimal netAmountBtc;
    private String description;
    private String depositAddress;
    private String visibility;
    private String confirmationMode;
    private Boolean amountLocked;
    private String referenceLabel;
    private Map<String, String> metadata = new LinkedHashMap<>();
    private String status; // "pending", "paid", "expired", "completed"
    private String txid;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String paymentRail = "ONCHAIN";
    private String paymentIntentStatus = "QUOTED";
    private String settlementReference;
    private Boolean terminal = false;

    public PaymentLinkDTO() {
    }

    public PaymentLinkDTO(String id, Long userId, BigDecimal amountBtc, String depositAddress) {
        this.id = id;
        this.userId = userId;
        this.amountBtc = amountBtc;
        this.depositAddress = depositAddress;
        this.status = "pending";
        refreshPaymentIntentCompatibility();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        refreshPaymentIntentCompatibility();
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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        refreshPaymentIntentCompatibility();
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
        refreshPaymentIntentCompatibility();
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

    public String getPaymentRail() {
        return paymentRail;
    }

    public void setPaymentRail(String paymentRail) {
        this.paymentRail = hasText(paymentRail) ? paymentRail : "ONCHAIN";
    }

    public String getPaymentIntentStatus() {
        return paymentIntentStatus;
    }

    public void setPaymentIntentStatus(String paymentIntentStatus) {
        this.paymentIntentStatus = hasText(paymentIntentStatus)
                ? paymentIntentStatus
                : mapPaymentIntentStatus(status);
        this.terminal = isTerminalPaymentIntentStatus(this.paymentIntentStatus);
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public void setSettlementReference(String settlementReference) {
        this.settlementReference = hasText(settlementReference)
                ? settlementReference
                : defaultSettlementReference();
    }

    public Boolean getTerminal() {
        return terminal;
    }

    public void setTerminal(Boolean terminal) {
        this.terminal = terminal;
    }

    private void refreshPaymentIntentCompatibility() {
        this.paymentRail = "ONCHAIN";
        this.paymentIntentStatus = mapPaymentIntentStatus(status);
        this.settlementReference = defaultSettlementReference();
        this.terminal = isTerminalPaymentIntentStatus(paymentIntentStatus);
    }

    private String mapPaymentIntentStatus(String status) {
        if (status == null) {
            return "REQUIRES_RECONCILIATION";
        }
        return switch (status.trim().toLowerCase()) {
            case "pending" -> "QUOTED";
            case "paid", "completed" -> "SETTLED";
            case "expired" -> "EXPIRED";
            case "cancelled", "canceled" -> "CANCELED";
            case "verifying_onboarding", "verifying_activation" -> "PROCESSING";
            default -> "REQUIRES_RECONCILIATION";
        };
    }

    private boolean isTerminalPaymentIntentStatus(String status) {
        return "SETTLED".equals(status)
                || "FAILED".equals(status)
                || "CANCELED".equals(status)
                || "EXPIRED".equals(status);
    }

    private String defaultSettlementReference() {
        if (hasText(txid)) {
            return txid;
        }
        return hasText(id) ? "payment-link:" + id : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
