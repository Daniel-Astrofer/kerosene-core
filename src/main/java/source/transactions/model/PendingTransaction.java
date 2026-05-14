package source.transactions.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Modelo para transações pendentes armazenadas no Redis
 */
public class PendingTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    private String txid;
    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private Long feeSatoshis;
    private String status; // PENDING, CONFIRMED, FAILED
    private Integer confirmations = 0;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private String errorMessage;
    private Long userId;
    private String rawTxHex;

    public PendingTransaction() {
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    public PendingTransaction(String txid, String fromAddress, String toAddress,
                            BigDecimal amount, Long feeSatoshis, Long userId) {
        this();
        this.txid = txid;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.amount = amount;
        this.feeSatoshis = feeSatoshis;
        this.userId = userId;
    }

    // Getters and Setters
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getFeeSatoshis() {
        return feeSatoshis;
    }

    public void setFeeSatoshis(Long feeSatoshis) {
        this.feeSatoshis = feeSatoshis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(Integer confirmations) {
        this.confirmations = confirmations;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRawTxHex() {
        return rawTxHex;
    }

    public void setRawTxHex(String rawTxHex) {
        this.rawTxHex = rawTxHex;
    }
}
