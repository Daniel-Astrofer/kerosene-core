package source.auth.dto.devicekey;

import source.auth.model.entity.DeviceKeyCredential;

import java.time.LocalDateTime;

public record DeviceKeyDeviceDTO(
        String credentialId,
        String deviceName,
        String deviceInstallId,
        String keyStorage,
        String platform,
        String browser,
        String onionServiceId,
        String status,
        long counter,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime revokedAt,
        int protocolVersion) {

    public static DeviceKeyDeviceDTO from(DeviceKeyCredential credential) {
        return new DeviceKeyDeviceDTO(
                credential.getCredentialId(),
                credential.getDeviceName(),
                credential.getDeviceInstallId(),
                credential.getKeyStorage(),
                credential.getPlatform(),
                credential.getBrowser(),
                credential.getOnionServiceId(),
                credential.getStatus(),
                credential.getCounter(),
                credential.getCreatedAt(),
                credential.getLastUsedAt(),
                credential.getRevokedAt(),
                credential.getProtocolVersion());
    }
}
