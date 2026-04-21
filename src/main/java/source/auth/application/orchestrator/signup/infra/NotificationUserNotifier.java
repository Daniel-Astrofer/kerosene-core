package source.auth.application.orchestrator.signup.infra;

import org.springframework.stereotype.Component;

import source.auth.application.orchestrator.signup.port.UserNotifier;
import source.notification.service.NotificationService;

@Component
public class NotificationUserNotifier implements UserNotifier {

    private final NotificationService notificationService;

    public NotificationUserNotifier(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notify(Long userId, String title, String body) {
        notificationService.notifyUser(userId, title, body);
    }
}
