package com.kerosene.auth.dto.passkey;

/** Input contract for proving possession of a registered passkey during authentication. */
public class PasskeyVerifyRequest {
    /** Account name whose registered passkey is being verified. */
    private String username;
    /** Signature proving possession of the passkey private key. */
    private String signature;
    /** Authenticator data supplied with the credential assertion. */
    private String authData;
    /** Client data JSON that binds the assertion to its challenge and origin. */
    private String clientDataJSON;
    /** Identifier of the registered credential selected for authentication. */
    private String credentialId;
    /** Stable device installation identity used by anti-replay and device policy checks. */
    private String deviceInstallId;

    /**
     * Returns account name whose registered passkey is being verified.
     * @return account username
     */
    public String getUsername() { return username; }
    /**
     * Sets account name whose registered passkey is being verified.
     * @param username account username
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * Returns signature proving possession of the passkey private key.
     * @return encoded signature
     */
    public String getSignature() { return signature; }
    /**
     * Sets signature proving possession of the passkey private key.
     * @param signature encoded signature
     */
    public void setSignature(String signature) { this.signature = signature; }
    /**
     * Returns authenticator data supplied with the credential assertion.
     * @return authenticator data
     */
    public String getAuthData() { return authData; }
    /**
     * Sets authenticator data supplied with the credential assertion.
     * @param authData authenticator data
     */
    public void setAuthData(String authData) { this.authData = authData; }
    /**
     * Returns client data JSON that binds the assertion to its challenge and origin.
     * @return client data JSON
     */
    public String getClientDataJSON() { return clientDataJSON; }
    /**
     * Sets client data JSON that binds the assertion to its challenge and origin.
     * @param clientDataJSON client data JSON
     */
    public void setClientDataJSON(String clientDataJSON) { this.clientDataJSON = clientDataJSON; }
    /**
     * Returns identifier of the registered credential selected for authentication.
     * @return credential identifier
     */
    public String getCredentialId() { return credentialId; }
    /**
     * Sets identifier of the registered credential selected for authentication.
     * @param credentialId credential identifier
     */
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    /**
     * Returns stable device installation identity used by anti-replay and device policy checks.
     * @return device installation ID
     */
    public String getDeviceInstallId() { return deviceInstallId; }
    /**
     * Sets stable device installation identity used by anti-replay and device policy checks.
     * @param deviceInstallId device installation ID
     */
    public void setDeviceInstallId(String deviceInstallId) { this.deviceInstallId = deviceInstallId; }
}
