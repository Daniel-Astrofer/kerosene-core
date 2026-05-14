package source.transactions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DepositDTO {

    private Long id;
    private Long userId;
    private String txid;
    private String fromAddress;
    private String toAddress;
    private BigDecimal amountBtc;
    private Long confirmations;
    private String status;  // "pending", "confirmed", "credited"
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    public DepositDTO() {
    }

    public DepositDTO(Long userId, String txid, String fromAddress, BigDecimal amountBtc) {
        this.userId = userId;
        this.txid = txid;
        this.fromAddress = fromAddress;
        this.amountBtc = amountBtc;
        this.status = "pending";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigDecimal getAmountBtc() {
        return amountBtc;
    }

    public void setAmountBtc(BigDecimal amountBtc) {
        this.amountBtc = amountBtc;
    }

    public Long getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(Long confirmations) {
        this.confirmations = confirmations;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
