package source.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

public class AdminLoginRequestDTO {
    private String username;

    @JsonAlias({"passphrase"})
    @JsonProperty("password")
    private char[] password;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String adminKeyProof;

    private String deviceId;
    private String deviceName;
    private String browser;
    private String userAgent;
    private String platform;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public String getAdminKeyProof() {
        return adminKeyProof;
    }

    public void setAdminKeyProof(String adminKeyProof) {
        this.adminKeyProof = adminKeyProof;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void wipePassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
