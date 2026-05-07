package source.bitcoinaccounts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cold_wallet_utxos", schema = "financial", indexes = {
        @Index(name = "idx_cold_wallet_utxos_wallet", columnList = "cold_wallet_id"),
        @Index(name = "idx_cold_wallet_utxos_outpoint", columnList = "txid,vout", unique = true),
        @Index(name = "idx_cold_wallet_utxos_status", columnList = "status")
})
public class ColdWalletUtxoEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "cold_wallet_id", nullable = false)
    private UUID coldWalletId;

    @Column(name = "txid", nullable = false, length = 128)
    private String txid;

    @Column(name = "vout", nullable = false)
    private int vout;

    @Column(name = "amount_sats", nullable = false)
    private long amountSats;

    @Column(name = "confirmations", nullable = false)
    private int confirmations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BitcoinAccountEnums.UtxoStatus status = BitcoinAccountEnums.UtxoStatus.UNSPENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getColdWalletId() {
        return coldWalletId;
    }

    public void setColdWalletId(UUID coldWalletId) {
        this.coldWalletId = coldWalletId;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public int getVout() {
        return vout;
    }

    public void setVout(int vout) {
        this.vout = vout;
    }

    public long getAmountSats() {
        return amountSats;
    }

    public void setAmountSats(long amountSats) {
        this.amountSats = amountSats;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(int confirmations) {
        this.confirmations = confirmations;
    }

    public BitcoinAccountEnums.UtxoStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.UtxoStatus status) {
        this.status = status;
    }
}
