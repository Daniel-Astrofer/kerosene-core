package source.auth.application.service.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.UserAppPinSettingsRepository;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.AppPinStatusDTO;
import source.auth.dto.ConfigureAppPinRequestDTO;
import source.auth.model.entity.UserAppPinSettings;
import source.auth.model.entity.UserDataBase;
import source.common.exception.ErrorCodes;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class AppPinService {

    private final UserAppPinSettingsRepository repository;
    private final Hasher hasher;
    private final TOTPVerifier totpVerifier;
    private final int minPinLength;
    private final int maxPinLength;
    private final int maxAttempts;
    private final int lockoutMinutes;

    public AppPinService(
            UserAppPinSettingsRepository repository,
            @Qualifier("Argon2Hasher") Hasher hasher,
            TOTPVerifier totpVerifier,
            @Value("${security.app-pin.min-length:4}") int minPinLength,
            @Value("${security.app-pin.max-length:8}") int maxPinLength,
            @Value("${security.app-pin.max-attempts:5}") int maxAttempts,
            @Value("${security.app-pin.lockout-minutes:5}") int lockoutMinutes) {
        this.repository = repository;
        this.hasher = hasher;
        this.totpVerifier = totpVerifier;
        this.minPinLength = minPinLength;
        this.maxPinLength = maxPinLength;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    @Transactional
    public AppPinStatusDTO configure(UserDataBase user, String deviceHash, ConfigureAppPinRequestDTO request) {
        String normalizedDeviceHash = normalizeDeviceHash(deviceHash);
        boolean enable = Boolean.TRUE.equals(request.getEnabled());
        UserAppPinSettings settings = repository.findByUserIdAndDeviceHash(user.getId(), normalizedDeviceHash)
                .map(this::resetExpiredLockIfNeeded)
                .orElseGet(() -> newSettings(user, normalizedDeviceHash));

        if (enable) {
            String newPin = normalizedPin(request.getPin(), true);
            if (isEnabled(settings)) {
                authorizeExistingSettings(user, settings, request.getCurrentPin(), request.getTotpCode());
            }

            settings.setEnabled(true);
            settings.setPinHash(hashPin(newPin));
            settings.setFailedAttempts(0);
            settings.setLockedUntil(null);
            settings.setLastVerifiedAt(null);
            repository.save(settings);
            return toStatus(user, settings);
        }

        if (isEnabled(settings)) {
            authorizeExistingSettings(user, settings, request.getCurrentPin(), request.getTotpCode());
        }

        settings.setEnabled(false);
        settings.setPinHash(null);
        settings.setFailedAttempts(0);
        settings.setLockedUntil(null);
        settings.setLastVerifiedAt(null);
        repository.save(settings);
        return toStatus(user, settings);
    }

    @Transactional
    public AppPinStatusDTO verify(UserDataBase user, String deviceHash, String pin) {
        String normalizedDeviceHash = normalizeDeviceHash(deviceHash);
        UserAppPinSettings settings = repository.findByUserIdAndDeviceHash(user.getId(), normalizedDeviceHash)
                .map(this::resetExpiredLockIfNeeded)
                .orElseThrow(() -> notConfigured(user, normalizedDeviceHash));

        if (!isEnabled(settings)) {
            throw notConfigured(user, normalizedDeviceHash);
        }

        assertNotLocked(user, settings);
        String normalizedPin = normalizedPin(pin, true);

        if (matchesPin(normalizedPin, settings.getPinHash())) {
            settings.setFailedAttempts(0);
            settings.setLockedUntil(null);
            settings.setLastVerifiedAt(LocalDateTime.now());
            repository.save(settings);
            return toStatus(user, settings);
        }

        int failedAttempts = (settings.getFailedAttempts() != null ? settings.getFailedAttempts() : 0) + 1;
        settings.setFailedAttempts(failedAttempts);

        if (failedAttempts >= maxAttempts) {
            settings.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
            repository.save(settings);
            throw new AuthExceptions.StructuredAuthException(
                    "PIN temporariamente bloqueado por excesso de tentativas.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCodes.AUTH_APP_PIN_LOCKED,
                    toStatus(user, settings));
        }

        repository.save(settings);
        throw new AuthExceptions.StructuredAuthException(
                "PIN numerico incorreto.",
                HttpStatus.UNAUTHORIZED,
                ErrorCodes.AUTH_APP_PIN_INVALID,
                toStatus(user, settings));
    }

    @Transactional
    public AppPinStatusDTO getStatus(UserDataBase user, String deviceHash) {
        String normalizedDeviceHash = normalizeOptionalDeviceHash(deviceHash);
        if (normalizedDeviceHash == null) {
            return toStatus(user, null);
        }
        UserAppPinSettings settings = repository.findByUserIdAndDeviceHash(user.getId(), normalizedDeviceHash)
                .map(this::resetExpiredLockIfNeeded)
                .orElse(null);
        return toStatus(user, settings);
    }

    private UserAppPinSettings newSettings(UserDataBase user, String deviceHash) {
        UserAppPinSettings settings = new UserAppPinSettings();
        settings.setUser(user);
        settings.setDeviceHash(deviceHash);
        settings.setEnabled(false);
        settings.setFailedAttempts(0);
        return settings;
    }

    private UserAppPinSettings resetExpiredLockIfNeeded(UserAppPinSettings settings) {
        if (settings.getLockedUntil() != null && LocalDateTime.now().isAfter(settings.getLockedUntil())) {
            settings.setLockedUntil(null);
            settings.setFailedAttempts(0);
            return repository.save(settings);
        }
        return settings;
    }

    private void authorizeExistingSettings(
            UserDataBase user,
            UserAppPinSettings settings,
            String currentPin,
            String totpCode) {
        String normalizedCurrentPin = normalizedPin(currentPin, false);
        if (normalizedCurrentPin != null) {
            assertNotLocked(user, settings);
            if (!matchesPin(normalizedCurrentPin, settings.getPinHash())) {
                throw new AuthExceptions.StructuredAuthException(
                        "PIN atual incorreto.",
                        HttpStatus.UNAUTHORIZED,
                        ErrorCodes.AUTH_APP_PIN_INVALID,
                        toStatus(user, settings));
            }
            return;
        }

        String normalizedTotp = normalizeTotpCode(totpCode);
        if (normalizedTotp != null) {
            if (!user.hasTotpEnabled()) {
                throw new AuthExceptions.InvalidCredentials(
                        "A conta nao possui TOTP ativo para redefinir o PIN do aplicativo.");
            }
            totpVerifier.totpVerify(user.getTOTPSecret(), normalizedTotp);
            return;
        }

        throw new AuthExceptions.InvalidCredentials(
                "Informe o PIN atual ou um codigo TOTP valido para alterar a protecao do aplicativo.");
    }

    private void assertNotLocked(UserDataBase user, UserAppPinSettings settings) {
        if (settings.getLockedUntil() != null && LocalDateTime.now().isBefore(settings.getLockedUntil())) {
            throw new AuthExceptions.StructuredAuthException(
                    "PIN temporariamente bloqueado por excesso de tentativas.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCodes.AUTH_APP_PIN_LOCKED,
                    toStatus(user, settings));
        }
    }

    private AuthExceptions.StructuredAuthException notConfigured(UserDataBase user, String deviceHash) {
        return new AuthExceptions.StructuredAuthException(
                "PIN numerico ainda nao configurado para este dispositivo.",
                HttpStatus.CONFLICT,
                ErrorCodes.AUTH_APP_PIN_NOT_CONFIGURED,
                getStatus(user, deviceHash));
    }

    private AppPinStatusDTO toStatus(UserDataBase user, UserAppPinSettings settings) {
        boolean enabled = isEnabled(settings);
        int failedAttempts = settings != null && settings.getFailedAttempts() != null ? settings.getFailedAttempts() : 0;
        boolean locked = settings != null
                && settings.getLockedUntil() != null
                && LocalDateTime.now().isBefore(settings.getLockedUntil());
        int remainingAttempts = locked ? 0 : Math.max(0, maxAttempts - failedAttempts);
        return new AppPinStatusDTO(
                enabled,
                enabled,
                locked,
                failedAttempts,
                remainingAttempts,
                maxAttempts,
                minPinLength,
                maxPinLength,
                user.hasTotpEnabled(),
                true,
                settings != null ? settings.getLockedUntil() : null,
                settings != null ? settings.getLastVerifiedAt() : null,
                settings != null ? settings.getUpdatedAt() : null);
    }

    private boolean isEnabled(UserAppPinSettings settings) {
        return settings != null
                && Boolean.TRUE.equals(settings.getEnabled())
                && settings.getPinHash() != null
                && !settings.getPinHash().isBlank();
    }

    private String normalizeDeviceHash(String deviceHash) {
        String normalized = normalizeOptionalDeviceHash(deviceHash);
        if (normalized == null) {
            throw new AuthExceptions.StructuredAuthException(
                    "O dispositivo atual nao foi identificado.",
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.AUTH_APP_PIN_DEVICE_REQUIRED,
                    null);
        }
        return normalized;
    }

    private String normalizeOptionalDeviceHash(String deviceHash) {
        if (deviceHash == null || deviceHash.isBlank()) {
            return null;
        }
        String normalized = deviceHash.trim();
        if (normalized.length() > 128) {
            throw new AuthExceptions.InvalidCredentials("Identificador do dispositivo invalido.");
        }
        return normalized;
    }

    private String normalizedPin(String pin, boolean required) {
        if (pin == null || pin.isBlank()) {
            if (required) {
                throw new AuthExceptions.InvalidCredentials("Informe um PIN numerico.");
            }
            return null;
        }

        String normalized = pin.trim();
        if (!normalized.chars().allMatch(Character::isDigit)) {
            throw new AuthExceptions.InvalidCredentials("O PIN deve conter apenas numeros.");
        }
        if (normalized.length() < minPinLength || normalized.length() > maxPinLength) {
            throw new AuthExceptions.InvalidCredentials(
                    "O PIN deve ter entre " + minPinLength + " e " + maxPinLength + " digitos.");
        }
        return normalized;
    }

    private String normalizeTotpCode(String totpCode) {
        if (totpCode == null || totpCode.isBlank()) {
            return null;
        }
        String normalized = totpCode.trim();
        if (!normalized.chars().allMatch(Character::isDigit) || normalized.length() != 6) {
            throw new AuthExceptions.InvalidCredentials("O codigo TOTP deve conter 6 digitos.");
        }
        return normalized;
    }

    private String hashPin(String pin) {
        char[] pinChars = pin.toCharArray();
        try {
            return hasher.hash(pinChars);
        } finally {
            Arrays.fill(pinChars, '\0');
        }
    }

    private boolean matchesPin(String pin, String pinHash) {
        char[] pinChars = pin.toCharArray();
        try {
            return Boolean.TRUE.equals(hasher.verify(pinChars, pinHash));
        } finally {
            Arrays.fill(pinChars, '\0');
        }
    }
}
