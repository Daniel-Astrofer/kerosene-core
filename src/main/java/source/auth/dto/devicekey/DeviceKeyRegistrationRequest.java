package source.auth.dto.devicekey;

public class DeviceKeyRegistrationRequest {
    private String publicKey;
    private String publicKeySha256;
    private String credentialId;
    private String userHandle;
    private String deviceName;
    private String deviceInstallId;
    private String keyStorage;
    private String platform;
    private String browser;
    private String brand;
    private String model;
    private String serialNumber;
    private String signedPayload;
    private String signature;

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getPublicKeySha256() { return publicKeySha256; }
    public void setPublicKeySha256(String publicKeySha256) { this.publicKeySha256 = publicKeySha256; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getUserHandle() { return userHandle; }
    public void setUserHandle(String userHandle) { this.userHandle = userHandle; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceInstallId() { return deviceInstallId; }
    public void setDeviceInstallId(String deviceInstallId) { this.deviceInstallId = deviceInstallId; }
    public String getKeyStorage() { return keyStorage; }
    public void setKeyStorage(String keyStorage) { this.keyStorage = keyStorage; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getSignedPayload() { return signedPayload; }
    public void setSignedPayload(String signedPayload) { this.signedPayload = signedPayload; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
