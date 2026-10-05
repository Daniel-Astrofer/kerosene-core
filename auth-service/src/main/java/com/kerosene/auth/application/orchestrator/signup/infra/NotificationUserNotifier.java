package com.kerosene.auth.application.orchestrator.signup.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kerosene.auth.application.orchestrator.signup.port.UserNotifier;
import com.kerosene.notification.model.UserNotificationPayload;
import com.kerosene.notification.service.NotificationService;

/** Adapts the notification service to the signup port using an independent post-commit transaction. */
@Component
public class NotificationUserNotifier implements UserNotifier {

    /** Notification delivery and persistence service. */
    private final NotificationService notificationService;

    /** Creates the notification adapter. */
    /** @param notificationService notification delivery service */
    public NotificationUserNotifier(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Sends the notification in a new transaction so persistence occurs after signup commit.
     * @param userId persisted account identifier
     * @param notification notification payload to dispatch
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long userId, UserNotificationPayload notification) {
        notificationService.notifyUser(userId, notification);
    }
}
