package source.auth.application.service.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.AdminAccessAttemptRepository;
import source.auth.application.infra.persistence.jpa.AdminAccessDeviceRepository;
import source.auth.application.infra.persistence.jpa.AdminAccessEventRepository;
import source.auth.application.infra.persistence.jpa.AdminKeyRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.AdminAccessAttemptDTO;
import source.auth.dto.AdminAuthenticatedDeviceDTO;
import source.auth.dto.AdminKeyCreateRequestDTO;
import source.auth.dto.AdminKeyStatusDTO;
import source.auth.dto.AdminLoginRequestDTO;
import source.auth.dto.AdminLoginResponseDTO;
import source.auth.dto.UserDTO;
import source.auth.model.entity.AdminAccessAttemptEntity;
import source.auth.model.entity.AdminAccessDeviceEntity;
import source.auth.model.entity.AdminAccessEventEntity;
import source.auth.model.entity.AdminKeyEntity;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AdminAccessAttemptStatus;
import source.auth.model.enums.AdminAccessDeviceStatus;
import source.auth.model.enums.AdminAccessEventStatus;
import source.auth.model.enums.AdminKeyStatus;
import source.auth.model.enums.UserRole;
import source.common.infra.logging.LogSanitizer;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;

@Service
public class AdminAccessService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessService.class);
    private static final int MAX_TEXT = 512;
    private static final long ATTEMPT_TTL_MINUTES = 5;

    private final LoginVerifier loginVerifier;
    private final JwtServicer jwtServicer;
    private final UserRepository userRepository;
    private final AdminKeyRepository adminKeyRepository;
    private final AdminAccessDeviceRepository deviceRepository;
    private final AdminAccessAttemptRepository attemptRepository;
    private final AdminAccessEventRepository eventRepository;
    private final NotificationService notificationService;

    public AdminAccessService(
            LoginVerifier loginVerifier,
            JwtServicer jwtServicer,
            UserRepository userRepository,
            AdminKeyRepository adminKeyRepository,
            AdminAccessDeviceRepository deviceRepository,
            AdminAccessAttemptRepository attemptRepository,
            AdminAccessEventRepository eventRepository,
            NotificationService notificationService) {
        this.loginVerifier = loginVerifier;
        this.jwtServicer = jwtServicer;
        this.userRepository = userRepository;
        this.adminKeyRepository = adminKeyRepository;
        this.deviceRepository = deviceRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public AdminLoginResponseDTO startLogin(
            AdminLoginRequestDTO request,
            String remoteAddress,
            String userAgentHeader) {
        UserDataBase user = authenticateCredentials(request);
        String deviceId = stableDeviceId(request, remoteAddress, userAgentHeader);
        String browser = safe(firstText(request.getBrowser(), "Navegador"));
        String userAgent = safe(firstText(request.getUserAgent(), userAgentHeader, request.getPlatform()));
        String ipFingerprint = LogSanitizer.fingerprint(remoteAddress);

        if (user.getRole() != UserRole.ADMIN) {
            recordEvent(user.getId(), deviceId, browser, userAgent, ipFingerprint, AdminAccessEventStatus.DENIED,
                    "user_not_admin");
            throw new AuthExceptions.StructuredAuthException(
                    "Acesso administrativo restrito.",
                    HttpStatus.FORBIDDEN,
                    "ADMIN_ROLE_REQUIRED",
                    null);
        }

        AdminKeyEntity activeKey = adminKeyRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), AdminKeyStatus.ACTIVE)
                .orElseThrow(() -> {
                    recordEvent(user.getId(), deviceId, browser, userAgent, ipFingerprint, AdminAccessEventStatus.DENIED,
                            "admin_key_missing");
                    return new AuthExceptions.StructuredAuthException(
                            "Chave de administracao nao configurada.",
                            HttpStatus.UNAUTHORIZED,
                            "ADMIN_KEY_REQUIRED",
                            null);
                });

        if (!constantTimeEquals(normalizeHash(request.getAdminKeyProof()), activeKey.getKeyMaterialHash())) {
            recordEvent(user.getId(), deviceId, browser, userAgent, ipFingerprint, AdminAccessEventStatus.DENIED,
                    "admin_key_invalid");
            throw new AuthExceptions.StructuredAuthException(
                    "Credenciais administrativas invalidas.",
                    HttpStatus.UNAUTHORIZED,
                    "ADMIN_KEY_INVALID",
                    null);
        }

        AdminAccessDeviceEntity device = upsertDevice(user, deviceId, request.getDeviceName(), browser, userAgent);
        if (device.getStatus() == AdminAccessDeviceStatus.BLOCKED
                || device.getStatus() == AdminAccessDeviceStatus.REVOKED) {
            recordEvent(user.getId(), deviceId, browser, userAgent, ipFingerprint, AdminAccessEventStatus.BLOCKED,
                    "device_blocked");
            throw new AuthExceptions.StructuredAuthException(
                    "Este dispositivo esta bloqueado para acesso administrativo.",
                    HttpStatus.LOCKED,
                    "ADMIN_DEVICE_BLOCKED",
                    null);
        }

        LocalDateTime now = LocalDateTime.now();
        AdminAccessAttemptEntity attempt = new AdminAccessAttemptEntity();
        attempt.setUser(user);
        attempt.setDevice(device);
        attempt.setStatus(AdminAccessAttemptStatus.PENDING);
        attempt.setBrowser(browser);
        attempt.setUserAgent(userAgent);
        attempt.setIpFingerprint(ipFingerprint);
        attempt.setRequestedAt(now);
        attempt.setExpiresAt(now.plusMinutes(ATTEMPT_TTL_MINUTES));
        attempt = attemptRepository.save(attempt);

        notifyMobile(user, attempt);

        return new AdminLoginResponseDTO(
                "PENDING",
                true,
                attempt.getId(),
                attempt.getExpiresAt(),
                null,
                "Aguardando autorizacao no app mobile.");
    }

    @Transactional
    public AdminLoginResponseDTO pollLogin(UUID attemptId, String remoteAddress, String userAgentHeader) {
        AdminAccessAttemptEntity attempt = attemptRepository.findForPollingById(attemptId)
                .orElseThrow(() -> new AuthExceptions.StructuredAuthException(
                        "Tentativa administrativa nao encontrada.",
                        HttpStatus.NOT_FOUND,
                        "ADMIN_ATTEMPT_NOT_FOUND",
                        null));

        validatePollingContext(attempt, remoteAddress, userAgentHeader);

        if (attempt.getStatus() == AdminAccessAttemptStatus.PENDING
                && attempt.getExpiresAt().isBefore(LocalDateTime.now())) {
            attempt.setStatus(AdminAccessAttemptStatus.EXPIRED);
            attempt.setDecidedAt(LocalDateTime.now());
            attemptRepository.save(attempt);
        }

        if (attempt.getStatus() == AdminAccessAttemptStatus.APPROVED) {
            attempt.getDevice().setStatus(AdminAccessDeviceStatus.ACTIVE);
            attempt.getDevice().setLastAccessAt(LocalDateTime.now());
            attempt.setStatus(AdminAccessAttemptStatus.REDEEMED);
            attemptRepository.save(attempt);
            deviceRepository.save(attempt.getDevice());
            String token = attempt.getUser().getId() + " "
                    + jwtServicer.generateToken(attempt.getUser().getId(), List.of(UserRole.ADMIN.name()));
            return new AdminLoginResponseDTO(
                    "APPROVED",
                    false,
                    attempt.getId(),
                    attempt.getExpiresAt(),
                    token,
                    "Acesso administrativo registrado.");
        }

        return new AdminLoginResponseDTO(
                attempt.getStatus().name(),
                attempt.getStatus() == AdminAccessAttemptStatus.PENDING,
                attempt.getId(),
                attempt.getExpiresAt(),
                null,
                statusMessage(attempt.getStatus()));
    }

    private void validatePollingContext(
            AdminAccessAttemptEntity attempt,
            String remoteAddress,
            String userAgentHeader) {
        String expectedIpFingerprint = safeId(attempt.getIpFingerprint());
        String requestIpFingerprint = LogSanitizer.fingerprint(remoteAddress);
        String expectedUserAgent = safe(attempt.getUserAgent());
        String requestUserAgent = safe(userAgentHeader);

        if (!constantTimeEquals(expectedIpFingerprint, requestIpFingerprint)
                || !constantTimeEquals(expectedUserAgent, requestUserAgent)) {
            throw new AuthExceptions.StructuredAuthException(
                    "Contexto da tentativa administrativa nao confere.",
                    HttpStatus.FORBIDDEN,
                    "ADMIN_ATTEMPT_CONTEXT_MISMATCH",
                    null);
        }
    }

    @Transactional(readOnly = true)
    public List<AdminAccessAttemptDTO> pendingAttempts(Long userId) {
        return attemptRepository
                .findByUserIdAndStatusAndExpiresAtAfterOrderByRequestedAtDesc(
                        userId,
                        AdminAccessAttemptStatus.PENDING,
                        LocalDateTime.now())
                .stream()
                .map(this::toAttemptDTO)
                .toList();
    }

    @Transactional
    public AdminAccessAttemptDTO decide(Long userId, UUID attemptId, String decision) {
        AdminAccessAttemptEntity attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new AuthExceptions.StructuredAuthException(
                        "Tentativa administrativa nao encontrada.",
                        HttpStatus.NOT_FOUND,
                        "ADMIN_ATTEMPT_NOT_FOUND",
                        null));

        if (attempt.getExpiresAt().isBefore(LocalDateTime.now())) {
            attempt.setStatus(AdminAccessAttemptStatus.EXPIRED);
            attempt.setDecidedAt(LocalDateTime.now());
            attemptRepository.save(attempt);
            return toAttemptDTO(attempt);
        }

        boolean approve = "APPROVE".equalsIgnoreCase(decision) || "ALLOW".equalsIgnoreCase(decision);
        AdminAccessDeviceEntity device = attempt.getDevice();
        attempt.setDecidedAt(LocalDateTime.now());

        if (approve) {
            attempt.setStatus(AdminAccessAttemptStatus.APPROVED);
            device.setStatus(AdminAccessDeviceStatus.ACTIVE);
            device.setLastAccessAt(LocalDateTime.now());
            recordEvent(userId, device.getDeviceId(), attempt.getBrowser(), attempt.getUserAgent(),
                    attempt.getIpFingerprint(), AdminAccessEventStatus.APPROVED, "mobile_approved");
        } else {
            attempt.setStatus(AdminAccessAttemptStatus.BLOCKED);
            device.setStatus(AdminAccessDeviceStatus.BLOCKED);
            recordEvent(userId, device.getDeviceId(), attempt.getBrowser(), attempt.getUserAgent(),
                    attempt.getIpFingerprint(), AdminAccessEventStatus.BLOCKED, "mobile_blocked");
        }

        deviceRepository.save(device);
        return toAttemptDTO(attemptRepository.save(attempt));
    }

    @Transactional
    public AdminKeyStatusDTO createOrRotateKey(Long userId, AdminKeyCreateRequestDTO request) {
        UserDataBase user = requireUser(userId);
        String hash = normalizeHash(request.getKeyMaterialHash());
        if (hash.length() < 32 || hash.length() > 128) {
            throw new AuthExceptions.StructuredAuthException(
                    "Material de chave administrativa invalido.",
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_KEY_HASH_INVALID",
                    null);
        }

        LocalDateTime now = LocalDateTime.now();
        List<AdminKeyEntity> existingKeys = adminKeyRepository.findByUserIdAndStatus(userId, AdminKeyStatus.ACTIVE);
        existingKeys.forEach(existing -> {
            existing.setStatus(AdminKeyStatus.REVOKED);
            existing.setRevokedAt(now);
            existing.setRotatedAt(now);
        });
        if (!existingKeys.isEmpty()) {
            adminKeyRepository.saveAll(existingKeys);
        }

        AdminKeyEntity key = new AdminKeyEntity();
        key.setUser(user);
        key.setKeyMaterialHash(hash);
        key.setKeyFingerprint(LogSanitizer.fingerprint(hash));
        key.setDeviceInstallId(safeId(request.getDeviceInstallId()));
        key.setStatus(AdminKeyStatus.ACTIVE);
        key = adminKeyRepository.save(key);

        return toKeyStatus(key);
    }

    @Transactional(readOnly = true)
    public AdminKeyStatusDTO keyStatus(Long userId) {
        return adminKeyRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, AdminKeyStatus.ACTIVE)
                .map(this::toKeyStatus)
                .orElse(new AdminKeyStatusDTO(false, "MISSING", null, null, null));
    }

    @Transactional
    public AdminKeyStatusDTO revokeKey(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<AdminKeyEntity> active = adminKeyRepository.findByUserIdAndStatus(userId, AdminKeyStatus.ACTIVE);
        active.forEach(key -> {
            key.setStatus(AdminKeyStatus.REVOKED);
            key.setRevokedAt(now);
        });
        if (!active.isEmpty()) {
            adminKeyRepository.saveAll(active);
        }
        return new AdminKeyStatusDTO(false, "REVOKED", null, null, now);
    }

    @Transactional(readOnly = true)
    public List<AdminAuthenticatedDeviceDTO> devices(Long userId) {
        return deviceRepository.findByUserIdOrderByLastAccessAtDesc(userId)
                .stream()
                .sorted(Comparator.comparing(AdminAccessDeviceEntity::getLastAccessAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDeviceDTO)
                .toList();
    }

    @Transactional
    public AdminAuthenticatedDeviceDTO changeDeviceStatus(Long userId, String deviceId, AdminAccessDeviceStatus status) {
        AdminAccessDeviceEntity device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseThrow(() -> new AuthExceptions.StructuredAuthException(
                        "Dispositivo administrativo nao encontrado.",
                        HttpStatus.NOT_FOUND,
                        "ADMIN_DEVICE_NOT_FOUND",
                        null));
        device.setStatus(status);
        device.setLastAccessAt(LocalDateTime.now());
        return toDeviceDTO(deviceRepository.save(device));
    }

    private UserDataBase authenticateCredentials(AdminLoginRequestDTO request) {
        UserDTO dto = new UserDTO();
        dto.setUsername(request.getUsername());
        dto.setPassword(request.getPassword());
        try {
            return loginVerifier.matcherWithoutDevice(dto);
        } finally {
            dto.setPassword(null);
            request.wipePassword();
        }
    }

    private AdminAccessDeviceEntity upsertDevice(
            UserDataBase user,
            String deviceId,
            String deviceName,
            String browser,
            String userAgent) {
        AdminAccessDeviceEntity device = deviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId)
                .orElseGet(AdminAccessDeviceEntity::new);
        device.setUser(user);
        device.setDeviceId(deviceId);
        device.setDeviceName(safe(firstText(deviceName, browser, "Sessao admin")));
        device.setBrowser(browser);
        device.setUserAgent(userAgent);
        if (device.getStatus() == null) {
            device.setStatus(AdminAccessDeviceStatus.PENDING);
        }
        device.setLastAccessAt(LocalDateTime.now());
        return deviceRepository.save(device);
    }

    private void notifyMobile(UserDataBase user, AdminAccessAttemptEntity attempt) {
        try {
            notificationService.notifyUser(
                    user.getId(),
                    NotificationMessages.payload(
                            NotificationKind.SECURITY_ADMIN_ACCESS_ATTEMPT,
                            NotificationSeverity.WARNING,
                            NotificationMessageKey.SECURITY_ADMIN_ACCESS_ATTEMPT,
                            "/settings",
                            "admin_access_attempt",
                            attempt.getId().toString(),
                            Map.of(
                                    "attemptId", attempt.getId().toString(),
                                    "browser", safe(firstText(attempt.getBrowser(), "Navegador")),
                                    "device", safe(firstText(attempt.getDevice().getDeviceName(), "Sessao admin")),
                                    "requestedAt", attempt.getRequestedAt().toString(),
                                    "status", attempt.getStatus().name())));
        } catch (RuntimeException exception) {
            log.warn("Falha ao notificar tentativa admin para userId={}", user.getId(), exception);
        }
    }

    private void recordEvent(
            Long adminId,
            String deviceId,
            String browser,
            String userAgent,
            String ipFingerprint,
            AdminAccessEventStatus status,
            String reason) {
        AdminAccessEventEntity event = new AdminAccessEventEntity();
        event.setAdminId(adminId);
        event.setDeviceId(safeId(deviceId));
        event.setBrowser(safe(browser));
        event.setSanitizedUserAgent(safe(userAgent));
        event.setIpFingerprint(safeId(ipFingerprint));
        event.setStatus(status);
        event.setReason(safe(reason));
        eventRepository.save(event);
    }

    private AdminAccessAttemptDTO toAttemptDTO(AdminAccessAttemptEntity attempt) {
        AdminAccessDeviceEntity device = attempt.getDevice();
        return new AdminAccessAttemptDTO(
                attempt.getId(),
                attempt.getStatus().name(),
                device.getDeviceId(),
                device.getDeviceName(),
                attempt.getBrowser(),
                attempt.getUserAgent(),
                attempt.getIpFingerprint(),
                attempt.getRequestedAt(),
                attempt.getExpiresAt());
    }

    private AdminAuthenticatedDeviceDTO toDeviceDTO(AdminAccessDeviceEntity device) {
        return new AdminAuthenticatedDeviceDTO(
                device.getDeviceId(),
                device.getDeviceName(),
                device.getBrowser(),
                device.getUserAgent(),
                device.getStatus().name(),
                device.getFirstAccessAt(),
                device.getLastAccessAt());
    }

    private AdminKeyStatusDTO toKeyStatus(AdminKeyEntity key) {
        return new AdminKeyStatusDTO(
                key.getStatus() == AdminKeyStatus.ACTIVE,
                key.getStatus().name(),
                key.getKeyFingerprint(),
                key.getCreatedAt(),
                key.getRevokedAt());
    }

    private UserDataBase requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthExceptions.StructuredAuthException(
                        "Usuario autenticado nao encontrado.",
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        null));
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                normalizeHash(left).getBytes(StandardCharsets.UTF_8),
                normalizeHash(right).getBytes(StandardCharsets.UTF_8));
    }

    private String stableDeviceId(AdminLoginRequestDTO request, String remoteAddress, String userAgentHeader) {
        String declared = request.getDeviceId();
        if (hasText(declared) && declared.length() <= 128) {
            return safeId(declared);
        }
        String fingerprintBase = firstText(request.getBrowser(), "browser") + "|"
                + firstText(request.getUserAgent(), userAgentHeader, "user-agent") + "|"
                + firstText(request.getPlatform(), "platform") + "|"
                + firstText(remoteAddress, "ip");
        return LogSanitizer.fingerprint(fingerprintBase);
    }

    private String statusMessage(AdminAccessAttemptStatus status) {
        return switch (status) {
            case PENDING -> "Aguardando autorizacao no app mobile.";
            case DENIED, BLOCKED -> "Acesso administrativo bloqueado no app mobile.";
            case EXPIRED -> "A tentativa de acesso expirou.";
            case REDEEMED -> "A tentativa de acesso administrativo ja foi utilizada.";
            case APPROVED -> "Acesso administrativo registrado.";
        };
    }

    private String normalizeHash(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeId(String value) {
        String sanitized = safe(value);
        return sanitized.length() > 128 ? LogSanitizer.fingerprint(sanitized) : sanitized;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() > MAX_TEXT ? sanitized.substring(0, MAX_TEXT) : sanitized;
    }
}
