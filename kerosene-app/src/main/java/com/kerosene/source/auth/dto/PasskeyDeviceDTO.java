package source.auth.dto;

import java.time.LocalDateTime;

public record PasskeyDeviceDTO(
        String credentialRef,
        String deviceName,
        String brand,
        String model,
        String serialNumber,
        String deviceInstallId,
        String platform,
        String browser,
        LocalDateTime firstAccessAt,
        LocalDateTime lastAccessAt,
        String status,
        String relyingPartyId,
        String originHost,
        String compatibilityStatus,
        boolean compatibleWithCurrentLogin) {
}
