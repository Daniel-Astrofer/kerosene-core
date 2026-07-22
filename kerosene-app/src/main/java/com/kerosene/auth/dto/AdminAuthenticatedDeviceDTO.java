package source.auth.dto;

import java.time.LocalDateTime;

public record AdminAuthenticatedDeviceDTO(
        String deviceId,
        String deviceName,
        String browser,
        String userAgent,
        String status,
        LocalDateTime firstAccessAt,
        LocalDateTime lastAccessAt) {
}
