package source.transactions.infra.externalpayments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.notification.model.UserNotificationPayload;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;

@Component
public class ExternalPaymentsNotificationAdapter implements ExternalPaymentsNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentsNotificationAdapter.class);

    private final source.notification.service.NotificationService notificationService;

    public ExternalPaymentsNotificationAdapter(source.notification.service.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notifyUser(Long userId, UserNotificationPayload notification) {
        try {
            notificationService.notifyUser(userId, notification);
        } catch (Exception ex) {
            log.warn("Failed to emit notification for external transfer: {}", ex.getMessage());
        }
    }
}
