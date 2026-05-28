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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_events", schema = "financial", indexes = {
        @Index(name = "idx_tax_events_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_tax_events_purge", columnList = "purge_after"),
        @Index(name = "idx_tax_events_type", columnList = "event_type")
})
public class TaxEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "jurisdiction", nullable = false, length = 32)
    private String jurisdiction = "UNSPECIFIED";

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 48)
    private BitcoinAccountEnums.TaxEventType eventType;

    @Column(name = "asset", nullable = false, length = 16)
    private String asset = "BTC";

    @Column(name = "quantity_sats", nullable = false)
    private long quantitySats;

    @Column(name = "fair_market_value", precision = 24, scale = 8)
    private BigDecimal fairMarketValue;

    @Column(name = "fiat_currency", length = 8)
    private String fiatCurrency;

    @Column(name = "cost_basis", precision = 24, scale = 8)
    private BigDecimal costBasis;

    @Column(name = "acquisition_date")
    private LocalDateTime acquisitionDate;

    @Column(name = "disposal_date")
    private LocalDateTime disposalDate;

    @Column(name = "source_txid", length = 128)
    private String sourceTxid;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "classification", nullable = false, length = 64)
    private String classification = "USER_CLASSIFICATION_PENDING";

    @Column(name = "metadata_redacted", nullable = false, columnDefinition = "TEXT")
    private String metadataRedacted = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "purge_after", nullable = false)
    private LocalDateTime purgeAfter;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (purgeAfter == null) {
            purgeAfter = createdAt.plusHours(24);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public BitcoinAccountEnums.TaxEventType getEventType() {
        return eventType;
    }

    public void setEventType(BitcoinAccountEnums.TaxEventType eventType) {
        this.eventType = eventType;
    }

    public long getQuantitySats() {
        return quantitySats;
    }

    public String getAsset() {
        return asset;
    }

    public void setQuantitySats(long quantitySats) {
        this.quantitySats = quantitySats;
    }

    public BigDecimal getFairMarketValue() {
        return fairMarketValue;
    }

    public void setFairMarketValue(BigDecimal fairMarketValue) {
        this.fairMarketValue = fairMarketValue;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    public String getSourceTxid() {
        return sourceTxid;
    }

    public void setSourceTxid(String sourceTxid) {
        this.sourceTxid = sourceTxid;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getMetadataRedacted() {
        return metadataRedacted;
    }

    public void setMetadataRedacted(String metadataRedacted) {
        this.metadataRedacted = metadataRedacted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPurgeAfter() {
        return purgeAfter;
    }

    public void setPurgeAfter(LocalDateTime purgeAfter) {
        this.purgeAfter = purgeAfter;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
