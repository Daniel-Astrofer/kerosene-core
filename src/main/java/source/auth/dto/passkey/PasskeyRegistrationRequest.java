package source.auth.dto.passkey;

public class PasskeyRegistrationRequest {
    private String publicKey;
    private String deviceName;
    private String signature;
    private String authData;
    private String clientDataJSON;
    private String credentialId;
    private String userHandle;
    private String publicKeyCose;
    private String brand;
    private String model;
    private String serialNumber;
    private String deviceInstallId;
    private String platform;
    private String browser;
    private String status;

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getAuthData() { return authData; }
    public void setAuthData(String authData) { this.authData = authData; }
    public String getClientDataJSON() { return clientDataJSON; }
    public void setClientDataJSON(String clientDataJSON) { this.clientDataJSON = clientDataJSON; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getUserHandle() { return userHandle; }
    public void setUserHandle(String userHandle) { this.userHandle = userHandle; }
    public String getPublicKeyCose() { return publicKeyCose; }
    public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getDeviceInstallId() { return deviceInstallId; }
    public void setDeviceInstallId(String deviceInstallId) { this.deviceInstallId = deviceInstallId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
