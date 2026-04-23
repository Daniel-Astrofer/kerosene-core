package source.ledger.application.transaction;

import source.notification.model.UserNotificationPayload;

public interface TransactionNotificationPort {

    void notifyUser(Long userId, UserNotificationPayload notification);

    default void notifyUser(Long userId, String title, String message) {
        notifyUser(userId, UserNotificationPayload.legacy(title, message));
    }
}
