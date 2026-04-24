package source.auth.dto;

import java.io.Serializable;
import source.auth.model.enums.AccountSecurityType;

/**
 * Holds the temporary onboarding state for a user while they complete the
 * multi-step authentication process (PoW -> optional TOTP -> Passkey).
 */
public class SignupState implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String username;

    /**
     * Hashed account password.
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

    // Passkey (Ed25519) optimized for Tor/Standard
    private String passkeyPublicKey;
    private String passkeyPublicKeyCose;
    private String passkeyCredentialId;
    private String passkeyUserHandle;
    private String passkeyDeviceName;
    private String passkeyRelyingPartyId;
    private String passkeyOriginHost;
    private String passkeyCredentialJson;

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

    private Integer shamirTotalShares;

    private Integer shamirThreshold;

    private Integer multisigThreshold;

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

    public String getPasskeyPublicKey() {
        return passkeyPublicKey;
    }

    public void setPasskeyPublicKey(String passkeyPublicKey) {
        this.passkeyPublicKey = passkeyPublicKey;
    }

    public String getPasskeyPublicKeyCose() {
        return passkeyPublicKeyCose;
    }

    public void setPasskeyPublicKeyCose(String passkeyPublicKeyCose) {
        this.passkeyPublicKeyCose = passkeyPublicKeyCose;
    }

    public String getPasskeyCredentialId() {
        return passkeyCredentialId;
    }

    public void setPasskeyCredentialId(String passkeyCredentialId) {
        this.passkeyCredentialId = passkeyCredentialId;
    }

    public String getPasskeyUserHandle() {
        return passkeyUserHandle;
    }

    public void setPasskeyUserHandle(String passkeyUserHandle) {
        this.passkeyUserHandle = passkeyUserHandle;
    }

    public String getPasskeyDeviceName() {
        return passkeyDeviceName;
    }

    public void setPasskeyDeviceName(String passkeyDeviceName) {
        this.passkeyDeviceName = passkeyDeviceName;
    }

    public String getPasskeyRelyingPartyId() {
        return passkeyRelyingPartyId;
    }

    public void setPasskeyRelyingPartyId(String passkeyRelyingPartyId) {
        this.passkeyRelyingPartyId = passkeyRelyingPartyId;
    }

    public String getPasskeyOriginHost() {
        return passkeyOriginHost;
    }

    public void setPasskeyOriginHost(String passkeyOriginHost) {
        this.passkeyOriginHost = passkeyOriginHost;
    }

    public String getPasskeyCredentialJson() {
        if (passkeyCredentialJson != null && !passkeyCredentialJson.isBlank()) {
            return passkeyCredentialJson;
        }
        if (passkeyCredentialId == null && passkeyPublicKeyCose == null && passkeyUserHandle == null) {
            return null;
        }
        return "{\"credentialId\":\"" + nullToEmpty(passkeyCredentialId)
                + "\",\"publicKeyCose\":\"" + nullToEmpty(passkeyPublicKeyCose)
                + "\",\"userHandle\":\"" + nullToEmpty(passkeyUserHandle)
                + "\",\"deviceName\":\"" + nullToEmpty(passkeyDeviceName)
                + "\",\"relyingPartyId\":\"" + nullToEmpty(passkeyRelyingPartyId)
                + "\",\"originHost\":\"" + nullToEmpty(passkeyOriginHost) + "\"}";
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

    public Integer getShamirTotalShares() {
        return shamirTotalShares;
    }

    public void setShamirTotalShares(Integer shamirTotalShares) {
        this.shamirTotalShares = shamirTotalShares;
    }

    public Integer getShamirThreshold() {
        return shamirThreshold;
    }

    public void setShamirThreshold(Integer shamirThreshold) {
        this.shamirThreshold = shamirThreshold;
    }

    public Integer getMultisigThreshold() {
        return multisigThreshold;
    }

    public void setMultisigThreshold(Integer multisigThreshold) {
        this.multisigThreshold = multisigThreshold;
    }

    public java.util.List<String> getBackupCodes() {
        return backupCodes;
    }

    public void setBackupCodes(java.util.List<String> backupCodes) {
        this.backupCodes = backupCodes;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
