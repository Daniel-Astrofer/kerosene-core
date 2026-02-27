package source.ledger.audit;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists a Merkle root checkpoint of all ledger balances.
 * Each row proves that, at a given moment, the set of all
 * internal balances hashed to a specific Merkle root — without
 * revealing individual wallet owners.
 */
@Entity
@Table(name = "merkle_audit", schema = "financial")
public class MerkleAuditEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * SHA-256 Merkle root of all (walletId || balance) leaves, sorted by walletId.
     */
    @Column(name = "merkle_root", nullable = false, length = 64)
    private String merkleRoot;

    /** Total number of ledger entries included in this snapshot. */
    @Column(name = "ledger_count", nullable = false)
    private Long ledgerCount;

    /**
     * Optional: Bitcoin txid of the OP_RETURN transaction that anchored
     * this root to the blockchain. Null until anchoring is performed.
     */
    @Column(name = "anchor_txid", length = 64)
    private String anchorTxid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MerkleAuditEntity() {
    }

    public MerkleAuditEntity(String merkleRoot, Long ledgerCount) {
        this.id = UUID.randomUUID();
        this.merkleRoot = merkleRoot;
        this.ledgerCount = ledgerCount;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public Long getLedgerCount() {
        return ledgerCount;
    }

    public String getAnchorTxid() {
        return anchorTxid;
    }

    public void setAnchorTxid(String anchorTxid) {
        this.anchorTxid = anchorTxid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
