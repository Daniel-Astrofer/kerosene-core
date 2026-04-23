package source.auth.application.orchestrator.signup.port;

import source.notification.model.UserNotificationPayload;

public interface UserNotifier {

    void notify(Long userId, UserNotificationPayload notification);

    default void notify(Long userId, String title, String body) {
        notify(userId, UserNotificationPayload.legacy(title, body));
    }
}
