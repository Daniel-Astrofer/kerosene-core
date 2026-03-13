package source.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_transaction_history", schema = "financial", indexes = {
        @jakarta.persistence.Index(name = "idx_ledger_history_sender", columnList = "sender_identifier"),
        @jakarta.persistence.Index(name = "idx_ledger_history_receiver", columnList = "receiver_user_id"),
        @jakarta.persistence.Index(name = "idx_ledger_history_created", columnList = "created_at"),
        @jakarta.persistence.Index(name = "idx_ledger_history_txid", columnList = "blockchain_txid"),
        @jakarta.persistence.Index(name = "idx_ledger_history_status", columnList = "status")
})
public class LedgerTransactionHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sender_identifier", nullable = false)
    private String senderIdentifier;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "receiver_identifier", nullable = false)
    private String receiverIdentifier;

    @Column(name = "receiver_user_id")
    private Long receiverUserId;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType; // INTERNAL, EXTERNAL_DEPOSIT, EXTERNAL_WITHDRAWAL

    @Column(name = "amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, CONCLUDED, CANCELED

    @Column(name = "network_fee", precision = 18, scale = 8)
    private BigDecimal networkFee;

    @Column(name = "blockchain_txid")
    private String blockchainTxid;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmations")
    private Integer confirmations;

    public LedgerTransactionHistory() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSenderIdentifier() {
        return senderIdentifier;
    }

    public void setSenderIdentifier(String senderIdentifier) {
        this.senderIdentifier = senderIdentifier;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getReceiverIdentifier() {
        return receiverIdentifier;
    }

    public void setReceiverIdentifier(String receiverIdentifier) {
        this.receiverIdentifier = receiverIdentifier;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(Long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
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

    public BigDecimal getNetworkFee() {
        return networkFee;
    }

    public void setNetworkFee(BigDecimal networkFee) {
        this.networkFee = networkFee;
    }

    public String getBlockchainTxid() {
        return blockchainTxid;
    }

    public void setBlockchainTxid(String blockchainTxid) {
        this.blockchainTxid = blockchainTxid;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(Integer confirmations) {
        this.confirmations = confirmations;
    }
}
