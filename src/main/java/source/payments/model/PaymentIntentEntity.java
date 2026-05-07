package source.payments.model;

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents", schema = "financial", indexes = {
        @Index(name = "idx_payment_intents_sender_created", columnList = "sender_user_id, created_at"),
        @Index(name = "idx_payment_intents_status", columnList = "status"),
        @Index(name = "idx_payment_intents_idempotency", columnList = "idempotency_key")
})
public class PaymentIntentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "locked_wallet_id")
    private Long lockedWalletId;

    @Column(name = "receiver_user_id")
    private Long receiverUserId;

    @Column(name = "receiver_display_name", length = 128)
    private String receiverDisplayName;

    @Column(name = "receiver_identifier", length = 255)
    private String receiverIdentifier;

    @Column(name = "external_destination", columnDefinition = "TEXT")
    private String externalDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "rail", nullable = false, length = 32)
    private PaymentEnums.PaymentRail rail;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_mode", nullable = false, length = 32)
    private PaymentEnums.FeeMode feeMode;

    @Column(name = "requested_amount_fiat", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmountFiat;

    @Column(name = "fiat_currency", nullable = false, length = 8)
    private String fiatCurrency = "BRL";

    @Column(name = "asset", nullable = false, length = 16)
    private String asset = "BTC";

    @Column(name = "requested_amount_sats", nullable = false)
    private Long requestedAmountSats;

    @Column(name = "receiver_amount_sats", nullable = false)
    private Long receiverAmountSats;

    @Column(name = "total_debit_sats", nullable = false)
    private Long totalDebitSats;

    @Column(name = "network_fee_sats", nullable = false)
    private Long networkFeeSats;

    @Column(name = "kerosene_fee_sats", nullable = false)
    private Long keroseneFeeSats;

    @Column(name = "fx_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal fxRate;

    @Column(name = "quote_expires_at", nullable = false)
    private Instant quoteExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentEnums.PaymentIntentStatus status = PaymentEnums.PaymentIntentStatus.CREATED;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "speed", length = 32)
    private PaymentEnums.OnchainSpeed speed;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public Long getLockedWalletId() {
        return lockedWalletId;
    }

    public void setLockedWalletId(Long lockedWalletId) {
        this.lockedWalletId = lockedWalletId;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(Long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public String getReceiverDisplayName() {
        return receiverDisplayName;
    }

    public void setReceiverDisplayName(String receiverDisplayName) {
        this.receiverDisplayName = receiverDisplayName;
    }

    public String getReceiverIdentifier() {
        return receiverIdentifier;
    }

    public void setReceiverIdentifier(String receiverIdentifier) {
        this.receiverIdentifier = receiverIdentifier;
    }

    public String getExternalDestination() {
        return externalDestination;
    }

    public void setExternalDestination(String externalDestination) {
        this.externalDestination = externalDestination;
    }

    public PaymentEnums.PaymentRail getRail() {
        return rail;
    }

    public void setRail(PaymentEnums.PaymentRail rail) {
        this.rail = rail;
    }

    public PaymentEnums.FeeMode getFeeMode() {
        return feeMode;
    }

    public void setFeeMode(PaymentEnums.FeeMode feeMode) {
        this.feeMode = feeMode;
    }

    public BigDecimal getRequestedAmountFiat() {
        return requestedAmountFiat;
    }

    public void setRequestedAmountFiat(BigDecimal requestedAmountFiat) {
        this.requestedAmountFiat = requestedAmountFiat;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public Long getRequestedAmountSats() {
        return requestedAmountSats;
    }

    public void setRequestedAmountSats(Long requestedAmountSats) {
        this.requestedAmountSats = requestedAmountSats;
    }

    public Long getReceiverAmountSats() {
        return receiverAmountSats;
    }

    public void setReceiverAmountSats(Long receiverAmountSats) {
        this.receiverAmountSats = receiverAmountSats;
    }

    public Long getTotalDebitSats() {
        return totalDebitSats;
    }

    public void setTotalDebitSats(Long totalDebitSats) {
        this.totalDebitSats = totalDebitSats;
    }

    public Long getNetworkFeeSats() {
        return networkFeeSats;
    }

    public void setNetworkFeeSats(Long networkFeeSats) {
        this.networkFeeSats = networkFeeSats;
    }

    public Long getKeroseneFeeSats() {
        return keroseneFeeSats;
    }

    public void setKeroseneFeeSats(Long keroseneFeeSats) {
        this.keroseneFeeSats = keroseneFeeSats;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public void setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
    }

    public Instant getQuoteExpiresAt() {
        return quoteExpiresAt;
    }

    public void setQuoteExpiresAt(Instant quoteExpiresAt) {
        this.quoteExpiresAt = quoteExpiresAt;
    }

    public PaymentEnums.PaymentIntentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentEnums.PaymentIntentStatus status) {
        this.status = status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public PaymentEnums.OnchainSpeed getSpeed() {
        return speed;
    }

    public void setSpeed(PaymentEnums.OnchainSpeed speed) {
        this.speed = speed;
    }

    public String getWarnings() {
        return warnings;
    }

    public void setWarnings(String warnings) {
        this.warnings = warnings;
    }
}
