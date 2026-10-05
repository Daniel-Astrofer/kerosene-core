package com.kerosene.auth.dto.passkey;

/** Input contract for registering a passkey, its signed attestation, and client device metadata. */
public class PasskeyRegistrationRequest {
    /** Public key representation registered for future passkey signature verification. */
    private String publicKey;
    /** Human-readable label shown in the account device inventory. */
    private String deviceName;
    /** Detached proof-of-possession signature for the registration response. */
    private String signature;
    /** Authenticator data returned by the WebAuthn-shaped credential response. */
    private String authData;
    /** Client data JSON covered by the registration signature. */
    private String clientDataJSON;
    /** Credential identifier that names the new passkey. */
    private String credentialId;
    /** Opaque account handle bound to the credential. */
    private String userHandle;
    /** COSE public key representation used by passkey verification. */
    private String publicKeyCose;
    /** Optional device manufacturer metadata. */
    private String brand;
    /** Optional device model metadata. */
    private String model;
    /** Optional serial metadata that can identify the physical device. */
    private String serialNumber;
    /** Stable client installation identity used for same-device credential policy. */
    private String deviceInstallId;
    /** Operating system or platform reported by the registering client. */
    private String platform;
    /** Browser identifier when enrollment is performed in a browser. */
    private String browser;
    /** Requested credential status metadata; server policy determines the persisted lifecycle state. */
    private String status;
    /** Explicit consent to unlink previous account credentials on this installation; false by default. */
    private boolean confirmUnlinkDevice;

    /**
     * Returns public key representation registered for future passkey signature verification.
     * @return public key material
     */
    public String getPublicKey() { return publicKey; }
    /**
     * Sets public key representation registered for future passkey signature verification.
     * @param publicKey public key material
     */
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    /**
     * Returns human-readable label shown in the account device inventory.
     * @return device name
     */
    public String getDeviceName() { return deviceName; }
    /**
     * Sets human-readable label shown in the account device inventory.
     * @param deviceName device name
     */
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    /**
     * Returns detached proof-of-possession signature for the registration response.
     * @return encoded signature
     */
    public String getSignature() { return signature; }
    /**
     * Sets detached proof-of-possession signature for the registration response.
     * @param signature encoded signature
     */
    public void setSignature(String signature) { this.signature = signature; }
    /**
     * Returns authenticator data returned by the WebAuthn-shaped credential response.
     * @return authenticator data
     */
    public String getAuthData() { return authData; }
    /**
     * Sets authenticator data returned by the WebAuthn-shaped credential response.
     * @param authData authenticator data
     */
    public void setAuthData(String authData) { this.authData = authData; }
    /**
     * Returns client data JSON covered by the registration signature.
     * @return client data JSON
     */
    public String getClientDataJSON() { return clientDataJSON; }
    /**
     * Sets client data JSON covered by the registration signature.
     * @param clientDataJSON client data JSON
     */
    public void setClientDataJSON(String clientDataJSON) { this.clientDataJSON = clientDataJSON; }
    /**
     * Returns credential identifier that names the new passkey.
     * @return credential identifier
     */
    public String getCredentialId() { return credentialId; }
    /**
     * Sets credential identifier that names the new passkey.
     * @param credentialId credential identifier
     */
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    /**
     * Returns opaque account handle bound to the credential.
     * @return user handle
     */
    public String getUserHandle() { return userHandle; }
    /**
     * Sets opaque account handle bound to the credential.
     * @param userHandle user handle
     */
    public void setUserHandle(String userHandle) { this.userHandle = userHandle; }
    /**
     * Returns cOSE public key representation used by passkey verification.
     * @return COSE public key
     */
    public String getPublicKeyCose() { return publicKeyCose; }
    /**
     * Sets cOSE public key representation used by passkey verification.
     * @param publicKeyCose COSE public key
     */
    public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }
    /**
     * Returns optional device manufacturer metadata.
     * @return device brand
     */
    public String getBrand() { return brand; }
    /**
     * Sets optional device manufacturer metadata.
     * @param brand device brand
     */
    public void setBrand(String brand) { this.brand = brand; }
    /**
     * Returns optional device model metadata.
     * @return device model
     */
    public String getModel() { return model; }
    /**
     * Sets optional device model metadata.
     * @param model device model
     */
    public void setModel(String model) { this.model = model; }
    /**
     * Returns optional serial metadata that can identify the physical device.
     * @return device serial number
     */
    public String getSerialNumber() { return serialNumber; }
    /**
     * Sets optional serial metadata that can identify the physical device.
     * @param serialNumber device serial number
     */
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    /**
     * Returns stable client installation identity used for same-device credential policy.
     * @return device installation ID
     */
    public String getDeviceInstallId() { return deviceInstallId; }
    /**
     * Sets stable client installation identity used for same-device credential policy.
     * @param deviceInstallId device installation ID
     */
    public void setDeviceInstallId(String deviceInstallId) { this.deviceInstallId = deviceInstallId; }
    /**
     * Returns operating system or platform reported by the registering client.
     * @return platform identifier
     */
    public String getPlatform() { return platform; }
    /**
     * Sets operating system or platform reported by the registering client.
     * @param platform platform identifier
     */
    public void setPlatform(String platform) { this.platform = platform; }
    /**
     * Returns browser identifier when enrollment is performed in a browser.
     * @return browser identifier
     */
    public String getBrowser() { return browser; }
    /**
     * Sets browser identifier when enrollment is performed in a browser.
     * @param browser browser identifier
     */
    public void setBrowser(String browser) { this.browser = browser; }
    /**
     * Returns requested credential status metadata; server policy determines the persisted lifecycle state.
     * @return requested status
     */
    public String getStatus() { return status; }
    /**
     * Sets requested credential status metadata; server policy determines the persisted lifecycle state.
     * @param status requested status
     */
    public void setStatus(String status) { this.status = status; }
    /**
     * Returns explicit consent to unlink previous account credentials on this installation; false by default.
     * @return unlink confirmation
     */
    public boolean isConfirmUnlinkDevice() { return confirmUnlinkDevice; }
    /**
     * Sets explicit consent to unlink previous account credentials on this installation; false by default.
     * @param confirmUnlinkDevice unlink confirmation
     */
    public void setConfirmUnlinkDevice(boolean confirmUnlinkDevice) { this.confirmUnlinkDevice = confirmUnlinkDevice; }
}
