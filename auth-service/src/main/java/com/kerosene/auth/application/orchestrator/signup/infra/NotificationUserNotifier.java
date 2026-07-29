package com.kerosene.auth.application.orchestrator.signup.infra;

import org.springframework.stereotype.Component;

import com.kerosene.auth.application.orchestrator.signup.port.UserNotifier;
import com.kerosene.notification.model.UserNotificationPayload;
import com.kerosene.notification.service.NotificationService;

@Component
public class NotificationUserNotifier implements UserNotifier {

    private final NotificationService notificationService;

    public NotificationUserNotifier(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notify(Long userId, UserNotificationPayload notification) {
        notificationService.notifyUser(userId, notification);
    }
}
