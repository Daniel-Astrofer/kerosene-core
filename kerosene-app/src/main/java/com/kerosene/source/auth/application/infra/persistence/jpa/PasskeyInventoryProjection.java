package source.auth.application.infra.persistence.jpa;

import java.time.LocalDateTime;

public record PasskeyInventoryProjection(
        byte[] credentialId,
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
        String originHost) {
}
