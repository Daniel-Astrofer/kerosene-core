package source.notification.dto;

public record DeviceTokenRegisterRequest(
        String platform,
        String token,
        String deviceId,
        String appVersion) {
}
