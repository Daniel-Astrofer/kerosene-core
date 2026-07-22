package source.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationDispatchAfterCommitListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatchAfterCommitListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationPort pushNotificationPort;

    public NotificationDispatchAfterCommitListener(
            SimpMessagingTemplate messagingTemplate,
            PushNotificationPort pushNotificationPort) {
        this.messagingTemplate = messagingTemplate;
        this.pushNotificationPort = pushNotificationPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationPersistedEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(event.userId()),
                    "/queue/notifications",
                    event.payload());

            logger.info(
                    "Pushed WebSocket notification to user: {} with kind='{}' title='{}'",
                    event.userId(),
                    event.payload().get("kind"),
                    event.payload().get("title"));
        } catch (Exception exception) {
            logger.error(
                    "Failed to push WebSocket notification to user {}: {}",
                    event.userId(),
                    exception.getMessage());
        }

        // Best-effort remote/local push fan-out (never fails the transaction path).
        try {
            pushNotificationPort.dispatch(event.userId(), event.payload());
        } catch (Exception exception) {
            logger.warn(
                    "PushNotificationPort failed for user {}: {}",
                    event.userId(),
                    exception.getMessage());
        }
    }
}
