package source.auth.dto.devicekey;

public class DeviceKeyVerifyRequest {
    private String username;
    private String credentialId;
    private String deviceInstallId;
    private String signedPayload;
    private String signature;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getDeviceInstallId() { return deviceInstallId; }
    public void setDeviceInstallId(String deviceInstallId) { this.deviceInstallId = deviceInstallId; }
    public String getSignedPayload() { return signedPayload; }
    public void setSignedPayload(String signedPayload) { this.signedPayload = signedPayload; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
