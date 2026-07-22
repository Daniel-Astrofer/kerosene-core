package source.notification.dto;

import java.util.Map;

public record NotificationSendRequest(
        String userId,
        String title,
        String body,
        String kind,
        String severity,
        String deeplink,
        String entityType,
        String entityId,
        Map<String, String> metadata) {
}
