package source.notification.dto;

import source.notification.model.entity.NotificationDeviceTokenEntity;

import java.time.LocalDateTime;

public record DeviceTokenResponse(
        Long id,
        String platform,
        String tokenRef,
        String deviceRef,
        String appVersion,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt,
        LocalDateTime revokedAt,
        boolean active) {

    public static DeviceTokenResponse from(NotificationDeviceTokenEntity entity) {
        return new DeviceTokenResponse(
                entity.getId(),
                entity.getPlatform(),
                entity.getTokenRef(),
                entity.getDeviceRef(),
                entity.getAppVersion(),
                entity.getCreatedAt(),
                entity.getLastSeenAt(),
                entity.getRevokedAt(),
                entity.getRevokedAt() == null);
    }
}
