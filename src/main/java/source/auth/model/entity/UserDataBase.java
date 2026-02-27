package source.auth.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import source.auth.model.contracts.UserDB;
import source.auth.model.enums.AccountSecurityType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Entity()
@Table(schema = "auth", name = "users_credentials", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "voucher_id" }) }, indexes = {
                @Index(name = "idx_users_username", columnList = "username", unique = true) })
public class UserDataBase implements UserDB {

    public UserDataBase() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "passphrase")
    private String passphrase;

    @Column(name = "totp_secret")
    private String totpSecret;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default false")
    private Boolean isActive = false;

    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "integer default 0")
    private Integer failedLoginAttempts = 0;

    /**
     * The account security mode chosen at signup.
     * Stored as a VARCHAR using the enum name.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_security", nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'STANDARD'")
    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;

    /**
     * AES-256-GCM encrypted co-signer secret (Base64-encoded IV + ciphertext).
     * Only populated for SHAMIR and MULTISIG_2FA modes.
     * NEVER exposed via any API or DTO — annotated @JsonIgnore as a safety net.
     */
    @JsonIgnore
    @Column(name = "platform_cosigner_secret", columnDefinition = "TEXT")
    private String platformCosignerSecret;

    @OneToOne
    @JoinColumn(name = "voucher_id", referencedColumnName = "id", unique = true)
    private Voucher voucher;

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    @Override
    public String getTOTPSecret() {
        return totpSecret;
    }

    @Override
    public void setTOTPSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    @Override
    public String getPassphrase() {
        return passphrase;
    }

    @Override
    public Long getId() {
        return this.id;
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

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public AccountSecurityType getAccountSecurity() {
        return accountSecurity;
    }

    public void setAccountSecurity(AccountSecurityType accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    /** Returns the AES-GCM ciphertext. Never expose this via API. */
    public String getPlatformCosignerSecret() {
        return platformCosignerSecret;
    }

    public void setPlatformCosignerSecret(String platformCosignerSecret) {
        this.platformCosignerSecret = platformCosignerSecret;
    }
}
