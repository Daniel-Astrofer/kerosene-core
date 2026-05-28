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
@Table(name = "receiving_addresses", schema = "financial", indexes = {
        @Index(name = "idx_receiving_addresses_card", columnList = "card_id"),
        @Index(name = "idx_receiving_addresses_address", columnList = "address", unique = true),
        @Index(name = "idx_receiving_addresses_status", columnList = "status")
})
public class ReceivingAddressEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "address", nullable = false, length = 128)
    private String address;

    @Column(name = "derivation_path", nullable = false, length = 160)
    private String derivationPath;

    @Column(name = "derivation_index", nullable = false)
    private int derivationIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_type", nullable = false, length = 16)
    private BitcoinAccountEnums.ScriptType scriptType = BitcoinAccountEnums.ScriptType.P2WPKH;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 48)
    private BitcoinAccountEnums.ReceivingAddressStatus status = BitcoinAccountEnums.ReceivingAddressStatus.UNUSED;

    @Column(name = "first_seen_txid", length = 128)
    private String firstSeenTxid;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

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

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDerivationPath() {
        return derivationPath;
    }

    public void setDerivationPath(String derivationPath) {
        this.derivationPath = derivationPath;
    }

    public int getDerivationIndex() {
        return derivationIndex;
    }

    public void setDerivationIndex(int derivationIndex) {
        this.derivationIndex = derivationIndex;
    }

    public BitcoinAccountEnums.ScriptType getScriptType() {
        return scriptType;
    }

    public void setScriptType(BitcoinAccountEnums.ScriptType scriptType) {
        this.scriptType = scriptType;
    }

    public BitcoinAccountEnums.ReceivingAddressStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.ReceivingAddressStatus status) {
        this.status = status;
    }

    public String getFirstSeenTxid() {
        return firstSeenTxid;
    }

    public void setFirstSeenTxid(String firstSeenTxid) {
        this.firstSeenTxid = firstSeenTxid;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
