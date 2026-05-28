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
@Table(name = "cold_wallets", schema = "financial", indexes = {
        @Index(name = "idx_cold_wallets_account", columnList = "account_id"),
        @Index(name = "idx_cold_wallets_fingerprint", columnList = "fingerprint")
})
public class ColdWalletEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "descriptor", columnDefinition = "TEXT")
    private String descriptor;

    @Column(name = "xpub", columnDefinition = "TEXT")
    private String xpub;

    @Column(name = "fingerprint", nullable = false, length = 32)
    private String fingerprint;

    @Column(name = "derivation_path", nullable = false, length = 160)
    private String derivationPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_policy", nullable = false, length = 32)
    private BitcoinAccountEnums.ScriptPolicy scriptPolicy = BitcoinAccountEnums.ScriptPolicy.SINGLE_SIG;

    @Column(name = "can_sign", nullable = false)
    private boolean canSign = false;

    @Column(name = "last_scan_height", nullable = false)
    private long lastScanHeight;

    @Column(name = "observed_balance_sats", nullable = false)
    private long observedBalanceSats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        canSign = false;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        canSign = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    public String getXpub() {
        return xpub;
    }

    public void setXpub(String xpub) {
        this.xpub = xpub;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getDerivationPath() {
        return derivationPath;
    }

    public void setDerivationPath(String derivationPath) {
        this.derivationPath = derivationPath;
    }

    public BitcoinAccountEnums.ScriptPolicy getScriptPolicy() {
        return scriptPolicy;
    }

    public void setScriptPolicy(BitcoinAccountEnums.ScriptPolicy scriptPolicy) {
        this.scriptPolicy = scriptPolicy;
    }

    public boolean isCanSign() {
        return canSign;
    }

    public long getLastScanHeight() {
        return lastScanHeight;
    }

    public void setLastScanHeight(long lastScanHeight) {
        this.lastScanHeight = lastScanHeight;
    }

    public long getObservedBalanceSats() {
        return observedBalanceSats;
    }

    public void setObservedBalanceSats(long observedBalanceSats) {
        this.observedBalanceSats = observedBalanceSats;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
