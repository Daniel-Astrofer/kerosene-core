package source.ledger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ─── LEDGER ENTRY (Separação Cirúrgica de Fundos) ─────────────────────────
 *
 * Cada transação registrada no sistema desce para esse Ledger de auditoria para
 * manter a transparência absoluta e evitar mistura (commingling) de fundos de
 * clientes com fundos da plataforma.
 */
@Entity
@Table(name = "ledger_entries", schema = "financial")
public class LedgerEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tx_id", nullable = false)
    private UUID txId;

    @Column(name = "user_id", nullable = false)
    private String userId; // ID do usuário ou "PLATFORM" para segregar as taxas

    @Column(name = "amount_net", precision = 18, scale = 8, nullable = false)
    private BigDecimal amountNet; // O valor que efetivamente pertence ao dono daquele user_id

    @Column(name = "fee_amount", precision = 18, scale = 8, nullable = false)
    private BigDecimal feeAmount; // A fatia da plataforma nesta transação específica

    @Column(name = "status", nullable = false)
    private String status; // PENDING (para sacar), COLLECTED (já siphoned)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public LedgerEntry() {
    }

    public LedgerEntry(UUID txId, String userId, BigDecimal amountNet, BigDecimal feeAmount, String status) {
        this.txId = txId;
        this.userId = userId;
        this.amountNet = amountNet;
        this.feeAmount = feeAmount;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTxId() {
        return txId;
    }

    public void setTxId(UUID txId) {
        this.txId = txId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getAmountNet() {
        return amountNet;
    }

    public void setAmountNet(BigDecimal amountNet) {
        this.amountNet = amountNet;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
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
}
