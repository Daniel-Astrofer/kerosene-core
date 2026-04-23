package source.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;

import java.util.Map;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyUser(Long userId, String title, String body) {
        notifyUser(userId, UserNotificationPayload.legacy(title, body));
    }

    public void notifyUser(
            Long userId,
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String body) {
        notifyUser(userId, UserNotificationPayload.create(kind, severity, title, body));
    }

    public void notifyUser(
            Long userId,
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String body,
            String deeplink,
            String entityType,
            String entityId,
            Map<String, String> metadata) {
        notifyUser(
                userId,
                UserNotificationPayload.create(
                        kind,
                        severity,
                        title,
                        body,
                        deeplink,
                        entityType,
                        entityId,
                        metadata));
    }

    /**
     * Sends a structured notification payload via WebSocket to a specific user queue.
     * The client should be subscribed to `/user/queue/notifications`.
     */
    public void notifyUser(Long userId, UserNotificationPayload payload) {
        try {
            // Sends to the specific user. Spring routes it to:
            // /user/{userId}/queue/notifications
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    payload.toMap());

            logger.info(
                    "Pushed WebSocket notification to user: {} with kind='{}' title='{}'",
                    userId,
                    payload.kind(),
                    payload.title());
        } catch (Exception e) {
            logger.error("Failed to push WebSocket notification to user {}: {}", userId, e.getMessage());
        }
    }
}
