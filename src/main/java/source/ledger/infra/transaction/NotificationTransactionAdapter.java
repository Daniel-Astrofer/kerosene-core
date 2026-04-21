package source.ledger.infra.transaction;

import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionNotificationPort;
import source.notification.service.NotificationService;

@Component
public class NotificationTransactionAdapter implements TransactionNotificationPort {

    private final NotificationService notificationService;

    public NotificationTransactionAdapter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notifyUser(Long userId, String title, String message) {
        notificationService.notifyUser(userId, title, message);
    }
}
