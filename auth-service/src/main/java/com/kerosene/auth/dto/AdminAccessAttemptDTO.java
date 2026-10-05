package com.kerosene.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sanitized administrator access request shown to a trusted device for approval or denial.
 * @param attemptId opaque attempt identifier
 * @param status current approval lifecycle state
 * @param deviceId requesting device identifier
 * @param deviceName requesting device label
 * @param browser browser reported by the login client
 * @param userAgent client user-agent string for review
 * @param ipFingerprint privacy-preserving fingerprint of the source network address
 * @param requestedAt time the access request was created
 * @param expiresAt time after which the request can no longer be approved
 */
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
