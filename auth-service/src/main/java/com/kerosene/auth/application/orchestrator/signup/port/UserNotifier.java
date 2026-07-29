package com.kerosene.auth.application.orchestrator.signup.port;

import com.kerosene.notification.model.UserNotificationPayload;

public interface UserNotifier {

    void notify(Long userId, UserNotificationPayload notification);
}
