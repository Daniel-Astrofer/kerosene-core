package com.kerosene.auth.dto;

import java.time.LocalDateTime;

/**
 * Public inventory row describing a device that has authenticated as an administrator.
 * @param deviceId stable device identity
 * @param deviceName administrator-assigned device label
 * @param browser browser used by the device
 * @param userAgent reported client agent
 * @param status current device trust or lifecycle state
 * @param firstAccessAt first recorded successful access
 * @param lastAccessAt most recent recorded successful access
 */
public record AdminAuthenticatedDeviceDTO(
        String deviceId,
        String deviceName,
        String browser,
        String userAgent,
        String status,
        LocalDateTime firstAccessAt,
        LocalDateTime lastAccessAt) {
}
