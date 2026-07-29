package com.kerosene.notification.service;

import java.util.Map;

/**
 * Optional remote push channel (FCM / APNs / vendor webhook).
 *
 * <p>Default implementation is logging-only: Kerosene is Tor-first and currently
 * delivers via STOMP + on-device background poll. Plug a real adapter when
 * product enables remote push without violating custody/privacy constraints.
 */
public interface PushNotificationPort {

    /**
     * Best-effort fan-out to registered device tokens for the user.
     * Must never throw into the notification persistence path.
     */
    void dispatch(Long userId, Map<String, Object> payload);
}
