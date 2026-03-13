package source.auth.dto;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import source.auth.model.enums.AccountSecurityType;

/**
 * Holds the temporary onboarding state for a user while they complete the
 * multi-step authentication process (PoW -> TOTP -> Passkey) and wait for
 * the mandatory Bitcoin payment confirmations.
 */
public class SignupState implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String username;

    /**
     * Hashed passphrase (done at signup time).
     */
    private char[] passphrase;

    /**
     * Raw TOTP seed — secret key for QR code scanning.
     * Needed during finalization to store in Postgres.
     */
    private String totpSecret;

    // Status flags
    private boolean isTotpVerified;
    private boolean isPasskeyRegistered;
    private boolean isPaymentConfirmed;

    // Generated Bitcoin onboarding deposit address
    private String btcDepositAddress;
    private String passkeyCredentialJson;

    // Sovereign Auth (Hardware Auth)
    private String hardwarePublicKey;
    private String hardwareDeviceName;

    /**
     * Backup codes are hashed one-time recovery secrets.
     */
    private java.util.List<String> backupCodes;

    /**
     * Security mode chosen by the user at signup.
     */
    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;

    /**
     * AES-256-GCM encrypted co-signer secret (Base64).
     * Populated during finalization for SHAMIR/MULTISIG_2FA.
     * Safe to store in Redis because it is already ciphertext.
     */
    private String platformCosignerSecret;

    public SignupState() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(char[] passphrase) {
        this.passphrase = passphrase;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isTotpVerified() {
        return isTotpVerified;
    }

    public void setTotpVerified(boolean totpVerified) {
        this.isTotpVerified = totpVerified;
    }

    public boolean isPasskeyRegistered() {
        return isPasskeyRegistered;
    }

    public void setPasskeyRegistered(boolean passkeyRegistered) {
        this.isPasskeyRegistered = passkeyRegistered;
    }

    public boolean isPaymentConfirmed() {
        return isPaymentConfirmed;
    }

    public void setPaymentConfirmed(boolean paymentConfirmed) {
        this.isPaymentConfirmed = paymentConfirmed;
    }

    public String getBtcDepositAddress() {
        return btcDepositAddress;
    }

    public void setBtcDepositAddress(String btcDepositAddress) {
        this.btcDepositAddress = btcDepositAddress;
    }

    public String getPasskeyCredentialJson() {
        return passkeyCredentialJson;
    }

    public void setPasskeyCredentialJson(String passkeyCredentialJson) {
        this.passkeyCredentialJson = passkeyCredentialJson;
    }

    public AccountSecurityType getAccountSecurity() {
        return accountSecurity;
    }

    public void setAccountSecurity(AccountSecurityType accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    public String getPlatformCosignerSecret() {
        return platformCosignerSecret;
    }

    public void setPlatformCosignerSecret(String platformCosignerSecret) {
        this.platformCosignerSecret = platformCosignerSecret;
    }

    public java.util.List<String> getBackupCodes() {
        return backupCodes;
    }

    public void setBackupCodes(java.util.List<String> backupCodes) {
        this.backupCodes = backupCodes;
    }

    public String getHardwarePublicKey() {
        return hardwarePublicKey;
    }

    public void setHardwarePublicKey(String hardwarePublicKey) {
        this.hardwarePublicKey = hardwarePublicKey;
    }

    public String getHardwareDeviceName() {
        return hardwareDeviceName;
    }

    public void setHardwareDeviceName(String hardwareDeviceName) {
        this.hardwareDeviceName = hardwareDeviceName;
    }
}
