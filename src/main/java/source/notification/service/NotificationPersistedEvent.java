package source.notification.service;

import java.util.Map;

public record NotificationPersistedEvent(Long userId, Map<String, Object> payload) {

    public NotificationPersistedEvent {
        payload = payload == null || payload.isEmpty() ? Map.of() : Map.copyOf(payload);
    }
}
