package source.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a notification payload via WebSocket to a specific user queue.
     * The client should be subscribed to `/user/queue/notifications`.
     *
     * @param userId The ID of the user receiving the notification.
     * @param title  The title of the notification.
     * @param body   The body content of the notification.
     */
    public void notifyUser(Long userId, String title, String body) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            payload.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // Sends to the specific user. Spring routes it to:
            // /user/{userId}/queue/notifications
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    payload);

            logger.info("Pushed WebSocket notification to user: {} with title: '{}'", userId, title);
        } catch (Exception e) {
            logger.error("Failed to push WebSocket notification to user {}: {}", userId, e.getMessage());
        }
    }
}
