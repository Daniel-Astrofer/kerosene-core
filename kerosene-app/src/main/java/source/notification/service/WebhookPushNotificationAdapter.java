package source.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import source.notification.model.entity.NotificationDeviceTokenEntity;
import source.notification.repository.NotificationDeviceTokenRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in remote push fan-out via operator webhook (not Google FCM hard-wired).
 *
 * <p>Enable with:
 * <pre>
 * notification.push.webhook.enabled=true
 * notification.push.webhook.url=https://push-relay.example/hooks/kerosene
 * notification.push.webhook.secret=...
 * </pre>
 *
 * Invoked by {@link CompositePushNotificationAdapter} when the property is true.
 * The relay may map payloads to FCM/APNs. Local-alert tokens are never sent.
 */
@Component
@ConditionalOnProperty(name = "notification.push.webhook.enabled", havingValue = "true")
public class WebhookPushNotificationAdapter implements PushNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(WebhookPushNotificationAdapter.class);

    private final NotificationDeviceTokenRepository deviceTokenRepository;
    private final NotificationDeviceTokenService deviceTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String webhookSecret;

    public WebhookPushNotificationAdapter(
            NotificationDeviceTokenRepository deviceTokenRepository,
            NotificationDeviceTokenService deviceTokenService,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${notification.push.webhook.url:}") String webhookUrl,
            @Value("${notification.push.webhook.secret:}") String webhookSecret,
            @Value("${notification.push.webhook.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${notification.push.webhook.read-timeout-ms:4000}") long readTimeoutMs) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.deviceTokenService = deviceTokenService;
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Override
    public void dispatch(Long userId, Map<String, Object> payload) {
        if (userId == null || payload == null) {
            return;
        }
        if (webhookUrl.isBlank()) {
            logger.warn("Push webhook enabled but notification.push.webhook.url is empty");
            return;
        }

        try {
            List<NotificationDeviceTokenEntity> devices =
                    deviceTokenRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
            List<Map<String, String>> remoteDevices = new ArrayList<>();
            int localAlert = 0;

            for (NotificationDeviceTokenEntity device : devices) {
                String plain = deviceTokenService.decryptTokenBestEffort(device);
                if (plain == null || plain.isBlank()) {
                    continue;
                }
                if (plain.startsWith("local-alert:")) {
                    localAlert += 1;
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("platform", device.getPlatform());
                row.put("token", plain);
                row.put("tokenRef", device.getTokenRef() == null ? "" : device.getTokenRef());
                if (device.getAppVersion() != null) {
                    row.put("appVersion", device.getAppVersion());
                }
                remoteDevices.add(row);
            }

            if (remoteDevices.isEmpty()) {
                logger.info(
                        "Push webhook skipped user={}: no remote tokens (localAlert={}) kind='{}'",
                        userId,
                        localAlert,
                        payload.get("kind"));
                return;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId);
            body.put("kind", payload.get("kind"));
            body.put("title", payload.get("title"));
            body.put("body", payload.get("body"));
            body.put("severity", payload.get("severity"));
            body.put("deeplink", payload.get("deeplink"));
            body.put("entityType", payload.get("entityType"));
            body.put("entityId", payload.get("entityId"));
            body.put("metadata", payload.get("metadata"));
            body.put("id", payload.get("id"));
            body.put("devices", remoteDevices);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!webhookSecret.isBlank()) {
                headers.set("X-Kerosene-Push-Secret", webhookSecret);
            }

            restTemplate.postForEntity(webhookUrl, new HttpEntity<>(body, headers), Void.class);
            logger.info(
                    "Push webhook delivered user={} remoteDevices={} kind='{}'",
                    userId,
                    remoteDevices.size(),
                    payload.get("kind"));
        } catch (RestClientResponseException exception) {
            logger.warn(
                    "Push webhook rejected user={} status={}: {}",
                    userId,
                    exception.getStatusCode().value(),
                    exception.getMessage());
        } catch (Exception exception) {
            logger.warn("Push webhook failed user={}: {}", userId, exception.getMessage());
        }
    }
}
