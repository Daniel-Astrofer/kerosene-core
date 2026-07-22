package source.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.notification.model.entity.NotificationDeviceTokenEntity;
import source.notification.repository.NotificationDeviceTokenRepository;

import java.util.List;
import java.util.Map;

/**
 * Observability push adapter (always registered):
 * <ul>
 *   <li>Counts active devices and classifies local-alert vs remote-capable tokens</li>
 *   <li>Does not call Google/Apple (privacy / Tor-first default)</li>
 *   <li>Decrypts only to classify token shape — never logs plaintext</li>
 * </ul>
 *
 * Invoked by {@link CompositePushNotificationAdapter}; do not inject as the sole port.
 */
@Component
public class LoggingPushNotificationAdapter implements PushNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPushNotificationAdapter.class);

    private final NotificationDeviceTokenRepository deviceTokenRepository;
    private final NotificationDeviceTokenService deviceTokenService;

    public LoggingPushNotificationAdapter(
            NotificationDeviceTokenRepository deviceTokenRepository,
            NotificationDeviceTokenService deviceTokenService) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.deviceTokenService = deviceTokenService;
    }

    @Override
    public void dispatch(Long userId, Map<String, Object> payload) {
        if (userId == null || payload == null) {
            return;
        }
        try {
            List<NotificationDeviceTokenEntity> devices =
                    deviceTokenRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
            int localAlert = 0;
            int remoteCapable = 0;
            int encrypted = 0;
            for (NotificationDeviceTokenEntity device : devices) {
                if (device.getTokenCiphertext() != null && !device.getTokenCiphertext().isBlank()) {
                    encrypted += 1;
                }
                String plain = deviceTokenService.decryptTokenBestEffort(device);
                if (plain != null && plain.startsWith("local-alert:")) {
                    localAlert += 1;
                } else if (plain != null && plain.length() >= 80) {
                    // Heuristic: FCM/APNs-style tokens are long opaque strings.
                    remoteCapable += 1;
                }
            }
            logger.info(
                    "Push channel: user={} devices={} encrypted={} localAlert={} remoteCapable={} kind='{}' title='{}'",
                    userId,
                    devices.size(),
                    encrypted,
                    localAlert,
                    remoteCapable,
                    payload.get("kind"),
                    payload.get("title"));
            if (remoteCapable > 0) {
                logger.info(
                        "Push channel: {} remote-capable token(s) present but no FCM adapter bean is configured.",
                        remoteCapable);
            }
        } catch (Exception exception) {
            logger.warn(
                    "Push channel log failed for user {}: {}",
                    userId,
                    exception.getMessage());
        }
    }
}
