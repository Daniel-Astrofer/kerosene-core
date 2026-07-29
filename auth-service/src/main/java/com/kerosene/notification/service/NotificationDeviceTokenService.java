package com.kerosene.notification.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.application.service.security.CosignerSecretService;
import com.kerosene.common.financial.FinancialNotificationAuditPort;
import com.kerosene.common.infra.logging.LogSanitizer;
import com.kerosene.notification.dto.DeviceTokenRegisterRequest;
import com.kerosene.notification.model.entity.NotificationDeviceTokenEntity;
import com.kerosene.notification.repository.NotificationDeviceTokenRepository;

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
    private final ObjectProvider<CosignerSecretService> cryptoService;

    public NotificationDeviceTokenService(
            NotificationDeviceTokenRepository repository,
            FinancialNotificationAuditPort auditPort,
            ObjectProvider<CosignerSecretService> cryptoService) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.cryptoService = cryptoService;
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
        entity.setTokenCiphertext(encryptTokenBestEffort(token));
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
                        "deviceRef", saved.getDeviceRef() != null ? saved.getDeviceRef() : "",
                        "ciphertext", saved.getTokenCiphertext() != null ? "present" : "absent"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationDeviceTokenEntity> activeTokens(Long userId) {
        requireUser(userId);
        return repository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
    }

    /**
     * Decrypt token for push adapters only. Never log the return value.
     */
    public String decryptTokenBestEffort(NotificationDeviceTokenEntity entity) {
        if (entity == null || entity.getTokenCiphertext() == null || entity.getTokenCiphertext().isBlank()) {
            return null;
        }
        CosignerSecretService crypto = cryptoService.getIfAvailable();
        if (crypto == null) {
            return null;
        }
        try {
            byte[] plain = crypto.decrypt(entity.getTokenCiphertext());
            if (plain == null) {
                return null;
            }
            // Cosigner pad may space-pad — trim.
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Transactional
    public void revoke(Long userId, Long tokenId) {
        requireUser(userId);
        if (tokenId == null) {
            throw new IllegalArgumentException("tokenId is required.");
        }
        repository.findByIdAndUserId(tokenId, userId).ifPresent(entity -> {
            entity.setRevokedAt(LocalDateTime.now());
            entity.setTokenCiphertext(null);
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

    private String encryptTokenBestEffort(String token) {
        CosignerSecretService crypto = cryptoService.getIfAvailable();
        if (crypto == null) {
            return null;
        }
        try {
            return crypto.encrypt(token.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            return null;
        }
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
