package com.kerosene.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Administrator device-session projection used to review and manage trusted devices.
 * @param id persistence identifier for the session row
 * @param deviceId stable device identity
 * @param deviceName administrator-assigned device label
 * @param browser browser reported by the client
 * @param platform operating system or platform family
 * @param status current session lifecycle state
 * @param firstAccessAt time the device first authenticated
 * @param lastAccessAt most recent authentication time
 */
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
