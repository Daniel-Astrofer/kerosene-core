package com.kerosene.auth.dto;

import java.io.Serial;
import java.io.Serializable;
import com.kerosene.auth.model.enums.AccountSecurityType;

/**
 * Holds the temporary onboarding state for a user while they complete the
 * multi-step authentication process (PoW -> optional TOTP -> Passkey).
 */
public class SignupState implements Serializable {
    /** Java serialization version for signup states stored between onboarding requests. */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Opaque identifier used to fetch and isolate this temporary signup transaction. */
    private String sessionId;
    /** Canonical account name chosen for this incomplete signup. */
    private String username;

    /**
     * Hashed account password retained in serialized form for final account creation.
     * A String is used for Jackson/Redis compatibility because char[] serialization is ambiguous across versions.
     */
    private String passphrase;

    /** TOTP seed required to verify enrollment and persist the authenticator secret during finalization. */
    private String totpSecret;

    // Status flags
    /** Whether the submitted authenticator code has been accepted for this signup. */
    private boolean isTotpVerified;
    /** Whether this onboarding session collected a passkey credential. */
    private boolean isPasskeyRegistered;
    /** Whether this onboarding session collected a device-key credential. */
    private boolean isDeviceKeyRegistered;
    /** Whether the required onboarding deposit was confirmed. */
    private boolean isPaymentConfirmed;

    // Generated Bitcoin onboarding deposit address
    /** Bitcoin deposit address generated for account onboarding. */
    private String btcDepositAddress;

    // Passkey (Ed25519) optimized for Tor/Standard
    /** Passkey public key material collected during enrollment. */
    private String passkeyPublicKey;
    /** COSE-encoded passkey public key representation. */
    private String passkeyPublicKeyCose;
    /** Credential identifier associated with the passkey. */
    private String passkeyCredentialId;
    /** Opaque user handle bound to the passkey credential. */
    private String passkeyUserHandle;
    /** User-facing device label associated with the passkey. */
    private String passkeyDeviceName;
    /** Relying-party identifier bound to the passkey enrollment. */
    private String passkeyRelyingPartyId;
    /** Origin host recorded for the passkey enrollment. */
    private String passkeyOriginHost;
    /** Optional device manufacturer metadata. */
    private String passkeyBrand;
    /** Optional device model metadata. */
    private String passkeyModel;
    /** Optional serial metadata that may identify the physical device. */
    private String passkeySerialNumber;
    /** Stable installation identity for device-scoped credential management. */
    private String passkeyDeviceInstallId;
    /** Operating system or platform reported during credential enrollment. */
    private String passkeyPlatform;
    /** Browser identifier reported during credential enrollment. */
    private String passkeyBrowser;
    /** Serialized passkey credential snapshot retained for compatibility with signup state readers. */
    private String passkeyCredentialJson;

    /** Hashed, one-time recovery codes associated with the pending account. */
    private java.util.List<String> backupCodes;

    /** Account security mode chosen during onboarding; defaults to STANDARD. */
    private AccountSecurityType accountSecurity = AccountSecurityType.STANDARD;

    /** Base64 AES-GCM ciphertext for the platform co-signer secret used by advanced modes and safe for Redis storage. */
    private String platformCosignerSecret;

    /** Total Shamir shares requested for recovery configuration. */
    private Integer shamirTotalShares;

    /** Minimum Shamir shares required to reconstruct the secret. */
    private Integer shamirThreshold;

    /** Number of factors required by the selected multisig profile. */
    private Integer multisigThreshold;

    /** Creates an empty onboarding state for JSON/session-store deserialization. */
    public SignupState() {
    }

    /**
     * Returns opaque identifier used to fetch and isolate this temporary signup transaction.
     *
     * @return sessionId value
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets opaque identifier used to fetch and isolate this temporary signup transaction.
     *
     * @param sessionId Opaque identifier used to fetch and isolate this temporary signup transaction.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns canonical account name chosen for this incomplete signup.
     *
     * @return username value
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets canonical account name chosen for this incomplete signup.
     *
     * @param username Canonical account name chosen for this incomplete signup.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns hashed account password retained in serialized form for final account creation.
     *
     * @return passphrase value
     */
    public String getPassphrase() {
        return passphrase;
    }

    /**
     * Sets hashed account password retained in serialized form for final account creation.
     *
     * @param passphrase Hashed account password retained in serialized form for final account creation.
     */
    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    /**
     * Returns TOTP seed needed to verify enrollment and persist the final authenticator secret.
     *
     * @return totpSecret value
     */
    public String getTotpSecret() {
        return totpSecret;
    }

    /**
     * Sets TOTP seed needed to verify enrollment and persist the final authenticator secret.
     *
     * @param totpSecret TOTP seed needed to verify enrollment and persist the final authenticator secret.
     */
    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    /**
     * Reports whether the TOTP factor was verified for this signup.
     */
    public boolean isTotpVerified() {
        return isTotpVerified;
    }

    /**
     * Records whether the TOTP factor was verified.
     * @param totpVerified true after successful code verification
     */
    public void setTotpVerified(boolean totpVerified) {
        this.isTotpVerified = totpVerified;
    }

    /**
     * Reports whether a passkey was registered in this signup.
     */
    public boolean isPasskeyRegistered() {
        return isPasskeyRegistered;
    }

    /**
     * Records whether a passkey was registered.
     * @param passkeyRegistered true after successful passkey enrollment
     */
    public void setPasskeyRegistered(boolean passkeyRegistered) {
        this.isPasskeyRegistered = passkeyRegistered;
    }

    /**
     * Reports whether a device key was registered in this signup.
     */
    public boolean isDeviceKeyRegistered() {
        return isDeviceKeyRegistered;
    }

    /**
     * Records whether a device key was registered.
     * @param deviceKeyRegistered true after successful device-key enrollment
     */
    public void setDeviceKeyRegistered(boolean deviceKeyRegistered) {
        this.isDeviceKeyRegistered = deviceKeyRegistered;
    }

    /**
     * Reports whether the onboarding deposit was confirmed.
     */
    public boolean isPaymentConfirmed() {
        return isPaymentConfirmed;
    }

    /**
     * Records whether the onboarding deposit was confirmed.
     * @param paymentConfirmed true after payment confirmation
     */
    public void setPaymentConfirmed(boolean paymentConfirmed) {
        this.isPaymentConfirmed = paymentConfirmed;
    }

    /**
     * Returns bitcoin deposit address generated for account onboarding.
     *
     * @return btcDepositAddress value
     */
    public String getBtcDepositAddress() {
        return btcDepositAddress;
    }

    /**
     * Sets bitcoin deposit address generated for account onboarding.
     *
     * @param btcDepositAddress Bitcoin deposit address generated for account onboarding.
     */
    public void setBtcDepositAddress(String btcDepositAddress) {
        this.btcDepositAddress = btcDepositAddress;
    }

    /**
     * Returns passkey public key material collected during enrollment.
     *
     * @return passkeyPublicKey value
     */
    public String getPasskeyPublicKey() {
        return passkeyPublicKey;
    }

    /**
     * Sets passkey public key material collected during enrollment.
     *
     * @param passkeyPublicKey Passkey public key material collected during enrollment.
     */
    public void setPasskeyPublicKey(String passkeyPublicKey) {
        this.passkeyPublicKey = passkeyPublicKey;
    }

    /**
     * Returns COSE-encoded passkey public key representation.
     *
     * @return passkeyPublicKeyCose value
     */
    public String getPasskeyPublicKeyCose() {
        return passkeyPublicKeyCose;
    }

    /**
     * Sets COSE-encoded passkey public key representation.
     *
     * @param passkeyPublicKeyCose COSE-encoded passkey public key representation.
     */
    public void setPasskeyPublicKeyCose(String passkeyPublicKeyCose) {
        this.passkeyPublicKeyCose = passkeyPublicKeyCose;
    }

    /**
     * Returns credential identifier associated with the passkey.
     *
     * @return passkeyCredentialId value
     */
    public String getPasskeyCredentialId() {
        return passkeyCredentialId;
    }

    /**
     * Sets credential identifier associated with the passkey.
     *
     * @param passkeyCredentialId Credential identifier associated with the passkey.
     */
    public void setPasskeyCredentialId(String passkeyCredentialId) {
        this.passkeyCredentialId = passkeyCredentialId;
    }

    /**
     * Returns opaque user handle bound to the passkey credential.
     *
     * @return passkeyUserHandle value
     */
    public String getPasskeyUserHandle() {
        return passkeyUserHandle;
    }

    /**
     * Sets opaque user handle bound to the passkey credential.
     *
     * @param passkeyUserHandle Opaque user handle bound to the passkey credential.
     */
    public void setPasskeyUserHandle(String passkeyUserHandle) {
        this.passkeyUserHandle = passkeyUserHandle;
    }

    /**
     * Returns user-facing device label associated with the passkey.
     *
     * @return passkeyDeviceName value
     */
    public String getPasskeyDeviceName() {
        return passkeyDeviceName;
    }

    /**
     * Sets user-facing device label associated with the passkey.
     *
     * @param passkeyDeviceName User-facing device label associated with the passkey.
     */
    public void setPasskeyDeviceName(String passkeyDeviceName) {
        this.passkeyDeviceName = passkeyDeviceName;
    }

    /**
     * Returns relying-party identifier bound to the passkey enrollment.
     *
     * @return passkeyRelyingPartyId value
     */
    public String getPasskeyRelyingPartyId() {
        return passkeyRelyingPartyId;
    }

    /**
     * Sets relying-party identifier bound to the passkey enrollment.
     *
     * @param passkeyRelyingPartyId Relying-party identifier bound to the passkey enrollment.
     */
    public void setPasskeyRelyingPartyId(String passkeyRelyingPartyId) {
        this.passkeyRelyingPartyId = passkeyRelyingPartyId;
    }

    /**
     * Returns origin host recorded for the passkey enrollment.
     *
     * @return passkeyOriginHost value
     */
    public String getPasskeyOriginHost() {
        return passkeyOriginHost;
    }

    /**
     * Sets origin host recorded for the passkey enrollment.
     *
     * @param passkeyOriginHost Origin host recorded for the passkey enrollment.
     */
    public void setPasskeyOriginHost(String passkeyOriginHost) {
        this.passkeyOriginHost = passkeyOriginHost;
    }

    /**
     * Returns optional device manufacturer metadata.
     *
     * @return passkeyBrand value
     */
    public String getPasskeyBrand() {
        return passkeyBrand;
    }

    /**
     * Sets optional device manufacturer metadata.
     *
     * @param passkeyBrand Optional device manufacturer metadata.
     */
    public void setPasskeyBrand(String passkeyBrand) {
        this.passkeyBrand = passkeyBrand;
    }

    /**
     * Returns optional device model metadata.
     *
     * @return passkeyModel value
     */
    public String getPasskeyModel() {
        return passkeyModel;
    }

    /**
     * Sets optional device model metadata.
     *
     * @param passkeyModel Optional device model metadata.
     */
    public void setPasskeyModel(String passkeyModel) {
        this.passkeyModel = passkeyModel;
    }

    /**
     * Returns optional serial metadata that may identify the physical device.
     *
     * @return passkeySerialNumber value
     */
    public String getPasskeySerialNumber() {
        return passkeySerialNumber;
    }

    /**
     * Sets optional serial metadata that may identify the physical device.
     *
     * @param passkeySerialNumber Optional serial metadata that may identify the physical device.
     */
    public void setPasskeySerialNumber(String passkeySerialNumber) {
        this.passkeySerialNumber = passkeySerialNumber;
    }

    /**
     * Returns stable installation identity for device-scoped credential management.
     *
     * @return passkeyDeviceInstallId value
     */
    public String getPasskeyDeviceInstallId() {
        return passkeyDeviceInstallId;
    }

    /**
     * Sets stable installation identity for device-scoped credential management.
     *
     * @param passkeyDeviceInstallId Stable installation identity for device-scoped credential management.
     */
    public void setPasskeyDeviceInstallId(String passkeyDeviceInstallId) {
        this.passkeyDeviceInstallId = passkeyDeviceInstallId;
    }

    /**
     * Returns operating system or platform reported during credential enrollment.
     *
     * @return passkeyPlatform value
     */
    public String getPasskeyPlatform() {
        return passkeyPlatform;
    }

    /**
     * Sets operating system or platform reported during credential enrollment.
     *
     * @param passkeyPlatform Operating system or platform reported during credential enrollment.
     */
    public void setPasskeyPlatform(String passkeyPlatform) {
        this.passkeyPlatform = passkeyPlatform;
    }

    /**
     * Returns browser identifier reported during credential enrollment.
     *
     * @return passkeyBrowser value
     */
    public String getPasskeyBrowser() {
        return passkeyBrowser;
    }

    /**
     * Sets browser identifier reported during credential enrollment.
     *
     * @param passkeyBrowser Browser identifier reported during credential enrollment.
     */
    public void setPasskeyBrowser(String passkeyBrowser) {
        this.passkeyBrowser = passkeyBrowser;
    }

    /**
     * Returns the stored credential JSON, or composes it from enrollment fields when no snapshot exists.
     *
     * @return complete JSON credential snapshot, or null when no credential data exists
     */
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
                + "\",\"originHost\":\"" + nullToEmpty(passkeyOriginHost)
                + "\",\"brand\":\"" + nullToEmpty(passkeyBrand)
                + "\",\"model\":\"" + nullToEmpty(passkeyModel)
                + "\",\"serialNumber\":\"" + nullToEmpty(passkeySerialNumber)
                + "\",\"deviceInstallId\":\"" + nullToEmpty(passkeyDeviceInstallId)
                + "\",\"platform\":\"" + nullToEmpty(passkeyPlatform)
                + "\",\"browser\":\"" + nullToEmpty(passkeyBrowser) + "\"}";
    }

    /**
     * Sets serialized passkey credential snapshot retained for compatibility with signup state readers.
     *
     * @param passkeyCredentialJson Serialized passkey credential snapshot retained for compatibility with signup state readers.
     */
    public void setPasskeyCredentialJson(String passkeyCredentialJson) {
        this.passkeyCredentialJson = passkeyCredentialJson;
    }

    /**
     * Returns account security mode chosen during onboarding; defaults to STANDARD.
     *
     * @return accountSecurity value
     */
    public AccountSecurityType getAccountSecurity() {
        return accountSecurity;
    }

    /**
     * Sets account security mode chosen during onboarding; defaults to STANDARD.
     *
     * @param accountSecurity Account security mode chosen during onboarding; defaults to STANDARD.
     */
    public void setAccountSecurity(AccountSecurityType accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    /**
     * Returns base64 AES-GCM ciphertext for the platform co-signer secret used by advanced modes.
     *
     * @return platformCosignerSecret value
     */
    public String getPlatformCosignerSecret() {
        return platformCosignerSecret;
    }

    /**
     * Sets base64 AES-GCM ciphertext for the platform co-signer secret used by advanced modes.
     *
     * @param platformCosignerSecret Base64 AES-GCM ciphertext for the platform co-signer secret used by advanced modes.
     */
    public void setPlatformCosignerSecret(String platformCosignerSecret) {
        this.platformCosignerSecret = platformCosignerSecret;
    }

    /**
     * Returns total Shamir shares requested for recovery configuration.
     *
     * @return shamirTotalShares value
     */
    public Integer getShamirTotalShares() {
        return shamirTotalShares;
    }

    /**
     * Sets total Shamir shares requested for recovery configuration.
     *
     * @param shamirTotalShares Total Shamir shares requested for recovery configuration.
     */
    public void setShamirTotalShares(Integer shamirTotalShares) {
        this.shamirTotalShares = shamirTotalShares;
    }

    /**
     * Returns minimum Shamir shares required to reconstruct the secret.
     *
     * @return shamirThreshold value
     */
    public Integer getShamirThreshold() {
        return shamirThreshold;
    }

    /**
     * Sets minimum Shamir shares required to reconstruct the secret.
     *
     * @param shamirThreshold Minimum Shamir shares required to reconstruct the secret.
     */
    public void setShamirThreshold(Integer shamirThreshold) {
        this.shamirThreshold = shamirThreshold;
    }

    /**
     * Returns number of factors required by the selected multisig profile.
     *
     * @return multisigThreshold value
     */
    public Integer getMultisigThreshold() {
        return multisigThreshold;
    }

    /**
     * Sets number of factors required by the selected multisig profile.
     *
     * @param multisigThreshold Number of factors required by the selected multisig profile.
     */
    public void setMultisigThreshold(Integer multisigThreshold) {
        this.multisigThreshold = multisigThreshold;
    }

    /**
     * Returns hashed, one-time recovery codes associated with the pending account.
     *
     * @return backupCodes value
     */
    public java.util.List<String> getBackupCodes() {
        return backupCodes;
    }

    /**
     * Sets hashed, one-time recovery codes associated with the pending account.
     *
     * @param backupCodes Hashed, one-time recovery codes associated with the pending account.
     */
    public void setBackupCodes(java.util.List<String> backupCodes) {
        this.backupCodes = backupCodes;
    }

    /** Converts a nullable string to empty text and escapes JSON quotes and backslashes.
     * @param value source metadata value
     * @return JSON-safe string, or empty text for null
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
