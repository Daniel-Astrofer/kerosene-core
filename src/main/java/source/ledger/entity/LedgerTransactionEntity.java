package source.ledger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_transactions", schema = "financial")
public class LedgerTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txid", nullable = false)
    private String txid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "to_address")
    private String toAddress;

    @Column(name = "amount", precision = 19, scale = 8)
    private BigDecimal amount;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LedgerTransactionEntity() {
        this.createdAt = LocalDateTime.now();
    }

    public LedgerTransactionEntity(String txid, Long userId, String toAddress, BigDecimal amount, String message) {
        this();
        this.txid = txid;
        this.userId = userId;
        this.toAddress = toAddress;
        this.amount = amount;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
