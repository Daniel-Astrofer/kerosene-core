package source.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InternalPaymentRequestDTO {
    private String id;
    private Long requesterUserId;
    private String receiverWalletName;
    private BigDecimal amount;
    private String status; // PENDING, PAID, CANCELED
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public InternalPaymentRequestDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(Long requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public String getReceiverWalletName() {
        return receiverWalletName;
    }

    public void setReceiverWalletName(String receiverWalletName) {
        this.receiverWalletName = receiverWalletName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
