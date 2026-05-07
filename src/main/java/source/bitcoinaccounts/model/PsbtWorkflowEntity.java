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
@Table(name = "psbt_workflows", schema = "financial", indexes = {
        @Index(name = "idx_psbt_workflows_wallet", columnList = "cold_wallet_id"),
        @Index(name = "idx_psbt_workflows_status", columnList = "status")
})
public class PsbtWorkflowEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "cold_wallet_id", nullable = false)
    private UUID coldWalletId;

    @Column(name = "unsigned_psbt", nullable = false, columnDefinition = "TEXT")
    private String unsignedPsbt;

    @Column(name = "signed_psbt", columnDefinition = "TEXT")
    private String signedPsbt;

    @Column(name = "destination_outputs_hash", nullable = false, length = 64)
    private String destinationOutputsHash;

    @Column(name = "destination_address", nullable = false, length = 128)
    private String destinationAddress;

    @Column(name = "amount_sats", nullable = false)
    private long amountSats;

    @Column(name = "selected_inputs_hash", nullable = false, length = 64)
    private String selectedInputsHash;

    @Column(name = "selected_outpoints", nullable = false, columnDefinition = "TEXT")
    private String selectedOutpoints;

    @Column(name = "change_output_hash", length = 64)
    private String changeOutputHash;

    @Column(name = "fee_rate", nullable = false)
    private long feeRate;

    @Column(name = "estimated_fee_sats", nullable = false)
    private long estimatedFeeSats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 48)
    private BitcoinAccountEnums.PsbtStatus status = BitcoinAccountEnums.PsbtStatus.DRAFT;

    @Column(name = "broadcast_txid", length = 128)
    private String broadcastTxid;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (expiresAt == null) {
            expiresAt = now.plusHours(24);
        }
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

    public String getUnsignedPsbt() {
        return unsignedPsbt;
    }

    public void setUnsignedPsbt(String unsignedPsbt) {
        this.unsignedPsbt = unsignedPsbt;
    }

    public String getSignedPsbt() {
        return signedPsbt;
    }

    public void setSignedPsbt(String signedPsbt) {
        this.signedPsbt = signedPsbt;
    }

    public String getDestinationOutputsHash() {
        return destinationOutputsHash;
    }

    public void setDestinationOutputsHash(String destinationOutputsHash) {
        this.destinationOutputsHash = destinationOutputsHash;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public long getAmountSats() {
        return amountSats;
    }

    public void setAmountSats(long amountSats) {
        this.amountSats = amountSats;
    }

    public String getSelectedInputsHash() {
        return selectedInputsHash;
    }

    public void setSelectedInputsHash(String selectedInputsHash) {
        this.selectedInputsHash = selectedInputsHash;
    }

    public String getSelectedOutpoints() {
        return selectedOutpoints;
    }

    public void setSelectedOutpoints(String selectedOutpoints) {
        this.selectedOutpoints = selectedOutpoints;
    }

    public String getChangeOutputHash() {
        return changeOutputHash;
    }

    public void setChangeOutputHash(String changeOutputHash) {
        this.changeOutputHash = changeOutputHash;
    }

    public long getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(long feeRate) {
        this.feeRate = feeRate;
    }

    public long getEstimatedFeeSats() {
        return estimatedFeeSats;
    }

    public void setEstimatedFeeSats(long estimatedFeeSats) {
        this.estimatedFeeSats = estimatedFeeSats;
    }

    public BitcoinAccountEnums.PsbtStatus getStatus() {
        return status;
    }

    public void setStatus(BitcoinAccountEnums.PsbtStatus status) {
        this.status = status;
    }

    public String getBroadcastTxid() {
        return broadcastTxid;
    }

    public void setBroadcastTxid(String broadcastTxid) {
        this.broadcastTxid = broadcastTxid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
