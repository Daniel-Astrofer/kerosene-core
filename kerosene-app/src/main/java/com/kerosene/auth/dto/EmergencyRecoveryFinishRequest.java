package source.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

public class EmergencyRecoveryFinishRequest {

    private String recoverySessionId;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String totpCode;

    private String publicKey;
    private String publicKeyCose;
    private String deviceName;
    private String signature;
    private String authData;
    private String clientDataJSON;
    private String credentialId;
    private String userHandle;
    private String deviceInstallId;
    private String brand;
    private String model;
    private String serialNumber;
    private String platform;
    private String browser;
    /** When true (default for recovery), unlinks another account bound to this device. */
    private boolean confirmUnlinkDevice = true;

    public String getRecoverySessionId() {
        return recoverySessionId;
    }

    public void setRecoverySessionId(String recoverySessionId) {
        this.recoverySessionId = recoverySessionId;
    }

    public String getTotpCode() {
        return totpCode;
    }

    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKeyCose() {
        return publicKeyCose;
    }

    public void setPublicKeyCose(String publicKeyCose) {
        this.publicKeyCose = publicKeyCose;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getAuthData() {
        return authData;
    }

    public void setAuthData(String authData) {
        this.authData = authData;
    }

    public String getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getUserHandle() {
        return userHandle;
    }

    public void setUserHandle(String userHandle) {
        this.userHandle = userHandle;
    }

    public String getDeviceInstallId() {
        return deviceInstallId;
    }

    public void setDeviceInstallId(String deviceInstallId) {
        this.deviceInstallId = deviceInstallId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public boolean isConfirmUnlinkDevice() {
        return confirmUnlinkDevice;
    }

    public void setConfirmUnlinkDevice(boolean confirmUnlinkDevice) {
        this.confirmUnlinkDevice = confirmUnlinkDevice;
    }
}
