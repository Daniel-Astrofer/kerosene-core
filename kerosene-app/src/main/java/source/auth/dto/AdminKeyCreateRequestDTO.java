package source.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AdminKeyCreateRequestDTO {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String keyMaterialHash;

    private String deviceInstallId;

    public String getKeyMaterialHash() {
        return keyMaterialHash;
    }

    public void setKeyMaterialHash(String keyMaterialHash) {
        this.keyMaterialHash = keyMaterialHash;
    }

    public String getDeviceInstallId() {
        return deviceInstallId;
    }

    public void setDeviceInstallId(String deviceInstallId) {
        this.deviceInstallId = deviceInstallId;
    }
}
