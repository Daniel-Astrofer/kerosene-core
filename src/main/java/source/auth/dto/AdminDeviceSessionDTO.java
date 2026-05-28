package source.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminDeviceSessionDTO(
        UUID id,
        String deviceId,
        String deviceName,
        String browser,
        String platform,
        String status,
        LocalDateTime firstAccessAt,
        LocalDateTime lastAccessAt) {
}
