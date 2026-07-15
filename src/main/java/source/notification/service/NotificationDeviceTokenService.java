package source.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.common.financial.FinancialNotificationAuditPort;
import source.notification.dto.DeviceTokenRegisterRequest;
import source.notification.model.entity.NotificationDeviceTokenEntity;
import source.notification.repository.NotificationDeviceTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationDeviceTokenService {

    private final NotificationDeviceTokenRepository repository;
    private final FinancialNotificationAuditPort auditPort;

    public NotificationDeviceTokenService(
            NotificationDeviceTokenRepository repository,
            FinancialNotificationAuditPort auditPort) {
        this.repository = repository;
        this.auditPort = auditPort;
    }

    @Transactional
    public NotificationDeviceTokenEntity register(Long userId, DeviceTokenRegisterRequest request) {
        requireUser(userId);
        String platform = normalizePlatform(request != null ? request.platform() : null);
        String token = normalizeToken(request != null ? request.token() : null);
        String tokenHash = sha256(token);
        LocalDateTime now = LocalDateTime.now();

        NotificationDeviceTokenEntity entity = repository.findByTokenHash(tokenHash)
                .orElseGet(NotificationDeviceTokenEntity::new);

        entity.setUserId(userId);
        entity.setPlatform(platform);
        entity.setTokenHash(tokenHash);
        entity.setTokenRef(LogSanitizer.fingerprint(token));
        entity.setDeviceRef(LogSanitizer.fingerprint(trim(request != null ? request.deviceId() : null, 128)));
        entity.setAppVersion(trim(request != null ? request.appVersion() : null, 64));
        entity.setLastSeenAt(now);
        entity.setRevokedAt(null);
        NotificationDeviceTokenEntity saved = repository.save(entity);

        auditPort.recordDeviceTokenEvent(
                "NOTIFICATION_DEVICE_TOKEN_REGISTERED",
                Map.of(
                        "entityType", "NOTIFICATION_DEVICE_TOKEN",
                        "entityId", String.valueOf(saved.getId()),
                        "userId", String.valueOf(userId),
                        "platform", platform,
                        "tokenRef", saved.getTokenRef(),
                        "deviceRef", saved.getDeviceRef() != null ? saved.getDeviceRef() : ""));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationDeviceTokenEntity> activeTokens(Long userId) {
        requireUser(userId);
        return repository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
    }

    @Transactional
    public void revoke(Long userId, Long tokenId) {
        requireUser(userId);
        if (tokenId == null) {
            throw new IllegalArgumentException("tokenId is required.");
        }
        repository.findByIdAndUserId(tokenId, userId).ifPresent(entity -> {
            entity.setRevokedAt(LocalDateTime.now());
            repository.save(entity);
            auditPort.recordDeviceTokenEvent(
                    "NOTIFICATION_DEVICE_TOKEN_REVOKED",
                    Map.of(
                            "entityType", "NOTIFICATION_DEVICE_TOKEN",
                            "entityId", String.valueOf(entity.getId()),
                            "userId", String.valueOf(userId),
                            "platform", entity.getPlatform(),
                            "tokenRef", entity.getTokenRef()));
        });
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
    }

    private String normalizePlatform(String value) {
        String platform = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (platform.isBlank()) {
            throw new IllegalArgumentException("platform is required.");
        }
        return switch (platform) {
            case "ANDROID", "IOS", "WEB" -> platform;
            default -> throw new IllegalArgumentException("platform must be ANDROID, IOS, or WEB.");
        };
    }

    private String normalizeToken(String value) {
        String token = value == null ? "" : value.trim();
        if (token.length() < 20 || token.length() > 4096) {
            throw new IllegalArgumentException("token length is invalid.");
        }
        return token;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash notification token", exception);
        }
    }
}
