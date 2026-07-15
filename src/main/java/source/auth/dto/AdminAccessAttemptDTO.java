package source.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminAccessAttemptDTO(
        UUID attemptId,
        String status,
        String deviceId,
        String deviceName,
        String browser,
        String userAgent,
        String ipFingerprint,
        LocalDateTime requestedAt,
        LocalDateTime expiresAt) {
}
