package source.transactions.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "network_transfers", schema = "financial", indexes = {
        @Index(name = "idx_network_transfers_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_network_transfers_wallet", columnList = "wallet_id"),
        @Index(name = "idx_network_transfers_status", columnList = "status")
})
public class ExternalTransferEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "wallet_name_snapshot", nullable = false)
    private String walletNameSnapshot;

    @Column(name = "network", nullable = false, length = 32)
    private String network;

    @Column(name = "transfer_type", nullable = false, length = 64)
    private String transferType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Convert(converter = source.security.persistence.StringCryptoConverter.class)
    @Column(name = "destination", columnDefinition = "TEXT")
    private String destination;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Convert(converter = source.security.persistence.StringCryptoConverter.class)
    @Column(name = "invoice_data", columnDefinition = "TEXT")
    private String invoiceData;

    @Column(name = "amount_btc", precision = 19, scale = 8)
    private BigDecimal amountBtc;

    @Column(name = "network_fee_btc", precision = 19, scale = 8)
    private BigDecimal networkFeeBtc;

    @Column(name = "platform_fee_btc", precision = 19, scale = 8)
    private BigDecimal platformFeeBtc;

    @Column(name = "total_debited_btc", precision = 19, scale = 8)
    private BigDecimal totalDebitedBtc;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public String getWalletNameSnapshot() {
        return walletNameSnapshot;
    }

    public void setWalletNameSnapshot(String walletNameSnapshot) {
        this.walletNameSnapshot = walletNameSnapshot;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getTransferType() {
        return transferType;
    }

    public void setTransferType(String transferType) {
        this.transferType = transferType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getInvoiceData() {
        return invoiceData;
    }

    public void setInvoiceData(String invoiceData) {
        this.invoiceData = invoiceData;
    }

    public BigDecimal getAmountBtc() {
        return amountBtc;
    }

    public void setAmountBtc(BigDecimal amountBtc) {
        this.amountBtc = amountBtc;
    }

    public BigDecimal getNetworkFeeBtc() {
        return networkFeeBtc;
    }

    public void setNetworkFeeBtc(BigDecimal networkFeeBtc) {
        this.networkFeeBtc = networkFeeBtc;
    }

    public BigDecimal getPlatformFeeBtc() {
        return platformFeeBtc;
    }

    public void setPlatformFeeBtc(BigDecimal platformFeeBtc) {
        this.platformFeeBtc = platformFeeBtc;
    }

    public BigDecimal getTotalDebitedBtc() {
        return totalDebitedBtc;
    }

    public void setTotalDebitedBtc(BigDecimal totalDebitedBtc) {
        this.totalDebitedBtc = totalDebitedBtc;
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

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
