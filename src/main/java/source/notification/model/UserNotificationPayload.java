package source.notification.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record UserNotificationPayload(
        String id,
        String kind,
        String severity,
        String title,
        String body,
        String timestamp,
        String createdAt,
        String deeplink,
        String entityType,
        String entityId,
        Map<String, String> metadata) {

    public UserNotificationPayload {
        metadata = sanitizeMetadata(metadata);
    }

    public static UserNotificationPayload legacy(String title, String body) {
        return create(
                NotificationKind.SYSTEM_INFO,
                NotificationSeverity.INFO,
                title,
                body,
                null,
                null,
                null,
                Map.of());
    }

    public static UserNotificationPayload create(
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String body) {
        return create(kind, severity, title, body, null, null, null, Map.of());
    }

    public static UserNotificationPayload create(
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String body,
            String deeplink,
            String entityType,
            String entityId,
            Map<String, String> metadata) {
        String now = Instant.now().toString();
        return new UserNotificationPayload(
                UUID.randomUUID().toString(),
                kind.wireValue(),
                severity.wireValue(),
                normalize(title),
                normalize(body),
                now,
                now,
                normalizeNullable(deeplink),
                normalizeNullable(entityType),
                normalizeNullable(entityId),
                metadata);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("kind", kind);
        payload.put("severity", severity);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("timestamp", timestamp);
        payload.put("createdAt", createdAt);

        if (deeplink != null) {
            payload.put("deeplink", deeplink);
        }
        if (entityType != null) {
            payload.put("entityType", entityType);
        }
        if (entityId != null) {
            payload.put("entityId", entityId);
        }
        if (!metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }

        return payload;
    }

    private static Map<String, String> sanitizeMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalizeNullable(entry.getKey());
            String value = normalizeNullable(entry.getValue());
            if (key != null && value != null) {
                normalized.put(key, value);
            }
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static String normalize(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "" : normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
