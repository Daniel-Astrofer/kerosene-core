package com.kerosene.auth.application.orchestrator.signup.port;

import com.kerosene.notification.model.UserNotificationPayload;

/** Port for sending account-related notifications from the signup workflow. */
public interface UserNotifier {

    /** Sends the supplied notification to a persisted user. */
    /** @param userId persisted account identifier */
    /** @param notification notification content and delivery metadata */
    void notify(Long userId, UserNotificationPayload notification);
}
