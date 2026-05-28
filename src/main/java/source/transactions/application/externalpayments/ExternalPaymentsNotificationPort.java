package source.transactions.application.externalpayments;

import source.notification.model.UserNotificationPayload;

public interface ExternalPaymentsNotificationPort {

    void notifyUser(Long userId, UserNotificationPayload notification);

    default void notifyUser(Long userId, String title, String body) {
        notifyUser(userId, UserNotificationPayload.legacy(title, body));
    }
}
