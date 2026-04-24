package source.wallet.model;

import source.auth.model.entity.UserDataBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets", schema = "financial", indexes = {
        @Index(name = "idx_user_wallet_name", columnList = "user_id, name", unique = true) })
public class WalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserDataBase user;

    @Convert(converter = source.security.persistence.StringCryptoConverter.class)
    @Column(name = "address", nullable = false)
    private String passphraseHash;

    @Column(name = "name", nullable = false)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true;

    @Convert(converter = source.security.persistence.StringCryptoConverter.class)
    @Column(name = "totp_secret", nullable = false)
    private String totpSecret;

    @Column(name = "deposit_address", length = 100)
    private String depositAddress;

    @Column(name = "lightning_address", length = 255)
    private String lightningAddress;

    @Column(name = "external_wallet_reference", length = 255)
    private String externalWalletReference;

    @Convert(converter = source.security.persistence.StringCryptoConverter.class)
    @Column(name = "xpub", columnDefinition = "TEXT")
    private String xpub;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_mode", nullable = false, length = 32)
    private WalletMode walletMode = WalletMode.KEROSENE;

    @Column(name = "last_derived_index", nullable = false, columnDefinition = "integer default -1")
    private Integer lastDerivedIndex = -1;

    @Column(name = "card_number_suffix", length = 4)
    private String cardNumberSuffix;

    @Column(name = "card_issued_at")
    private LocalDateTime cardIssuedAt;

    @Column(name = "card_expires_at")
    private LocalDateTime cardExpiresAt;

    @Column(name = "card_last_rotated_at")
    private LocalDateTime cardLastRotatedAt;

    @Column(name = "card_sequence", nullable = false, columnDefinition = "integer default 1")
    private Integer cardSequence = 1;

    @Column(name = "previous_card_number_suffix", length = 4)
    private String previousCardNumberSuffix;

    @Column(name = "previous_card_expires_at")
    private LocalDateTime previousCardExpiresAt;

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.toUpperCase() : null;
    }

    public Long getId() {
        return id;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public String getPassphraseHash() {
        return passphraseHash;
    }

    public void setPassphraseHash(String passphraseHash) {
        this.passphraseHash = passphraseHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public String getDepositAddress() {
        return depositAddress;
    }

    public void setDepositAddress(String depositAddress) {
        this.depositAddress = depositAddress;
    }

    public String getLightningAddress() {
        return lightningAddress;
    }

    public void setLightningAddress(String lightningAddress) {
        this.lightningAddress = lightningAddress;
    }

    public String getExternalWalletReference() {
        return externalWalletReference;
    }

    public void setExternalWalletReference(String externalWalletReference) {
        this.externalWalletReference = externalWalletReference;
    }

    public String getXpub() {
        return xpub;
    }

    public void setXpub(String xpub) {
        this.xpub = xpub;
    }

    public WalletMode getWalletMode() {
        return walletMode != null ? walletMode : WalletMode.KEROSENE;
    }

    public void setWalletMode(WalletMode walletMode) {
        this.walletMode = walletMode != null ? walletMode : WalletMode.KEROSENE;
    }

    public boolean isSelfCustodyMode() {
        return getWalletMode().isSelfCustody();
    }

    public boolean isKeroseneCustodyMode() {
        return getWalletMode().isKerosene();
    }

    public Integer getLastDerivedIndex() {
        return lastDerivedIndex;
    }

    public void setLastDerivedIndex(Integer lastDerivedIndex) {
        this.lastDerivedIndex = lastDerivedIndex;
    }

    public String getCardNumberSuffix() {
        return cardNumberSuffix;
    }

    public void setCardNumberSuffix(String cardNumberSuffix) {
        this.cardNumberSuffix = cardNumberSuffix;
    }

    public LocalDateTime getCardIssuedAt() {
        return cardIssuedAt;
    }

    public void setCardIssuedAt(LocalDateTime cardIssuedAt) {
        this.cardIssuedAt = cardIssuedAt;
    }

    public LocalDateTime getCardExpiresAt() {
        return cardExpiresAt;
    }

    public void setCardExpiresAt(LocalDateTime cardExpiresAt) {
        this.cardExpiresAt = cardExpiresAt;
    }

    public LocalDateTime getCardLastRotatedAt() {
        return cardLastRotatedAt;
    }

    public void setCardLastRotatedAt(LocalDateTime cardLastRotatedAt) {
        this.cardLastRotatedAt = cardLastRotatedAt;
    }

    public Integer getCardSequence() {
        return cardSequence;
    }

    public void setCardSequence(Integer cardSequence) {
        this.cardSequence = cardSequence;
    }

    public String getPreviousCardNumberSuffix() {
        return previousCardNumberSuffix;
    }

    public void setPreviousCardNumberSuffix(String previousCardNumberSuffix) {
        this.previousCardNumberSuffix = previousCardNumberSuffix;
    }

    public LocalDateTime getPreviousCardExpiresAt() {
        return previousCardExpiresAt;
    }

    public void setPreviousCardExpiresAt(LocalDateTime previousCardExpiresAt) {
        this.previousCardExpiresAt = previousCardExpiresAt;
    }
}
