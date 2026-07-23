package com.kerosene.auth.application.service.passkey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyInventoryProjection;
import com.kerosene.auth.application.service.devicekey.DeviceKeyService;
import com.kerosene.auth.dto.DeviceCredentialChallengeDTO;
import com.kerosene.auth.dto.PasskeyActionRequiredDTO;
import com.kerosene.auth.dto.PasskeyDeviceDTO;
import com.kerosene.auth.dto.PasskeyInventoryDTO;
import com.kerosene.auth.dto.devicekey.DeviceKeyChallengeResponse;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.common.infra.logging.LogSanitizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PasskeyInventoryService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyInventoryService.class);

    public static final String FACTOR_DEVICE_KEY = "DEVICE_KEY";
    public static final String FACTOR_PASSKEY = "PASSKEY";

    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final DeviceKeyCredentialRepository deviceKeyCredentialRepository;
    private final PasskeyService passkeyService;
    private final DeviceKeyService deviceKeyService;
    private final long passkeyChallengeTtlSeconds;
    private final boolean preferDeviceKey;

    public PasskeyInventoryService(
            PasskeyCredentialRepository passkeyCredentialRepository,
            DeviceKeyCredentialRepository deviceKeyCredentialRepository,
            PasskeyService passkeyService,
            DeviceKeyService deviceKeyService,
            @Value("${webauthn.challenge-ttl-seconds:90}") long passkeyChallengeTtlSeconds,
            @Value("${kerosene.auth.prefer-device-key:true}") boolean preferDeviceKey) {
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.deviceKeyCredentialRepository = deviceKeyCredentialRepository;
        this.passkeyService = passkeyService;
        this.deviceKeyService = deviceKeyService;
        this.passkeyChallengeTtlSeconds = passkeyChallengeTtlSeconds > 0 ? passkeyChallengeTtlSeconds : 90L;
        this.preferDeviceKey = preferDeviceKey;
    }

    public PasskeyInventoryDTO inventoryFor(UserDataBase user) {
        List<PasskeyInventoryProjection> credentials = passkeyCredentialRepository.findInventoryByUserId(user.getId());
        String currentRpId = passkeyService.resolveCurrentRelyingPartyId();
        String currentHost = passkeyService.resolveCurrentRequestHost();

        List<PasskeyDeviceDTO> devices = credentials.stream()
                .map(credential -> toDevice(credential, currentRpId, currentHost))
                .toList();

        boolean compatibleForCurrentLogin = devices.stream().anyMatch(PasskeyDeviceDTO::compatibleWithCurrentLogin);
        boolean legacyCredentialsPresent = devices.stream()
                .anyMatch(device -> "UNKNOWN".equals(device.compatibilityStatus()));

        return new PasskeyInventoryDTO(
                !devices.isEmpty(),
                compatibleForCurrentLogin,
                legacyCredentialsPresent,
                currentRpId,
                currentHost,
                devices);
    }

    public boolean hasUsablePasskeyForCurrentLogin(UserDataBase user) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        return inventory.compatibleForCurrentLogin() || inventory.legacyCredentialsPresent();
    }

    public boolean hasActiveDeviceKey(UserDataBase user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return deviceKeyCredentialRepository.existsActiveByUserId(user.getId());
    }

    public boolean isKnownIncompatibleForCurrentLogin(PasskeyCredential credential) {
        return compatibilityOf(
                credential.getRelyingPartyId(),
                credential.getOriginHost(),
                passkeyService.resolveCurrentRelyingPartyId(),
                passkeyService.resolveCurrentRequestHost()) == CompatibilityStatus.INCOMPATIBLE;
    }

    public boolean isKnownIncompatibleForCurrentLogin(String relyingPartyId, String originHost) {
        return compatibilityOf(
                relyingPartyId,
                originHost,
                passkeyService.resolveCurrentRelyingPartyId(),
                passkeyService.resolveCurrentRequestHost()) == CompatibilityStatus.INCOMPATIBLE;
    }

    /**
     * Builds a 428-style payload with typed challenges.
     *
     * @param passkeyChallengeHex optional pre-issued passkey challenge (may be null)
     */
    public PasskeyActionRequiredDTO buildChallengeRequired(UserDataBase user, String passkeyChallengeHex, String reason) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        List<String> acceptedFactors = new ArrayList<>(2);
        Map<String, DeviceCredentialChallengeDTO> challenges = new LinkedHashMap<>();

        boolean hasDeviceKey = hasActiveDeviceKey(user);
        boolean hasPasskeyMaterial = inventory.passkeyRegistered()
                || inventory.compatibleForCurrentLogin()
                || inventory.legacyCredentialsPresent();

        if (hasDeviceKey) {
            try {
                DeviceKeyChallengeResponse deviceChallenge = deviceKeyService.startAuthenticationChallenge(user);
                acceptedFactors.add(FACTOR_DEVICE_KEY);
                challenges.put(
                        FACTOR_DEVICE_KEY,
                        DeviceCredentialChallengeDTO.deviceKey(
                                deviceChallenge.challengeId(),
                                deviceChallenge.challenge(),
                                deviceChallenge.expiresInSeconds(),
                                deviceChallenge.onionServiceId(),
                                deviceChallenge.algorithm(),
                                deviceChallenge.canonicalization()));
            } catch (Exception exception) {
                log.warn(
                        "Unable to issue DEVICE_KEY challenge for userId={}: {}",
                        user.getId(),
                        exception.getMessage());
            }
        }

        String passkeyChallenge = passkeyChallengeHex;
        if ((passkeyChallenge == null || passkeyChallenge.isBlank())
                && (hasPasskeyMaterial || !challenges.containsKey(FACTOR_DEVICE_KEY))) {
            passkeyChallenge = passkeyService.generateChallenge(user.getUsername());
        }
        if (passkeyChallenge != null && !passkeyChallenge.isBlank()) {
            acceptedFactors.add(FACTOR_PASSKEY);
            challenges.put(
                    FACTOR_PASSKEY,
                    DeviceCredentialChallengeDTO.passkey(passkeyChallenge, passkeyChallengeTtlSeconds));
        }

        String preferredFactor = resolvePreferredFactor(acceptedFactors, hasDeviceKey);
        // Legacy single challenge: keep PASSKEY hex when present so old clients keep working;
        // they still fetch DEVICE_KEY challenge separately when enrolled locally.
        String legacyChallenge = challenges.containsKey(FACTOR_PASSKEY)
                ? challenges.get(FACTOR_PASSKEY).challenge()
                : challenges.containsKey(FACTOR_DEVICE_KEY)
                        ? challenges.get(FACTOR_DEVICE_KEY).challenge()
                        : passkeyChallengeHex;

        return new PasskeyActionRequiredDTO(
                "ASSERT_PASSKEY",
                reason,
                legacyChallenge,
                user.hasTotpEnabled(),
                shouldLinkNewPasskey(user, inventory),
                "/settings/security/passkeys",
                guidanceFor(user, inventory, true),
                inventory,
                List.copyOf(acceptedFactors),
                Map.copyOf(challenges),
                preferredFactor);
    }

    public PasskeyActionRequiredDTO buildLinkNewPasskeyGuidance(UserDataBase user, String reason) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        return new PasskeyActionRequiredDTO(
                "LINK_NEW_PASSKEY",
                reason,
                null,
                user.hasTotpEnabled(),
                shouldLinkNewPasskey(user, inventory),
                "/settings/security/passkeys",
                guidanceFor(user, inventory, false),
                inventory,
                List.of(),
                Map.of(),
                null);
    }

    /**
     * Counter replay / desync — not "credential missing". Never first-prompt re-link.
     */
    public PasskeyActionRequiredDTO buildReplayConflictGuidance(UserDataBase user, String reason) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        String guidance = reason != null && !reason.isBlank()
                ? reason
                : "Possivel conflito de seguranca no contador da chave deste dispositivo. "
                        + "Tente novamente. Se o problema continuar, entre com senha + TOTP e revise os dispositivos.";
        return new PasskeyActionRequiredDTO(
                "SECURITY_CONFLICT",
                "DEVICE_CREDENTIAL_REPLAY",
                null,
                user.hasTotpEnabled(),
                false,
                "",
                guidance,
                inventory,
                List.of(),
                Map.of(),
                null);
    }

    /**
     * Soft-lock after repeated replay failures on the same credential.
     */
    public PasskeyActionRequiredDTO buildReplayLockedGuidance(UserDataBase user, long lockSeconds) {
        PasskeyInventoryDTO inventory = inventoryFor(user);
        long minutes = Math.max(1L, (lockSeconds + 59L) / 60L);
        String guidance = "Chave do dispositivo temporariamente bloqueada por possivel conflito de seguranca. "
                + "Aguarde cerca de " + minutes + " minuto(s) ou entre com senha + TOTP e revise os dispositivos.";
        return new PasskeyActionRequiredDTO(
                "DEVICE_CREDENTIAL_LOCKED",
                "DEVICE_CREDENTIAL_REPLAY_LOCKED",
                null,
                user.hasTotpEnabled(),
                false,
                "",
                guidance,
                inventory,
                List.of(),
                Map.of(),
                null);
    }

    private String resolvePreferredFactor(List<String> acceptedFactors, boolean hasDeviceKey) {
        if (preferDeviceKey && hasDeviceKey && acceptedFactors.contains(FACTOR_DEVICE_KEY)) {
            return FACTOR_DEVICE_KEY;
        }
        if (acceptedFactors.contains(FACTOR_PASSKEY)) {
            return FACTOR_PASSKEY;
        }
        if (acceptedFactors.contains(FACTOR_DEVICE_KEY)) {
            return FACTOR_DEVICE_KEY;
        }
        return null;
    }

    private PasskeyDeviceDTO toDevice(PasskeyInventoryProjection credential, String currentRpId, String currentHost) {
        CompatibilityStatus compatibility = compatibilityOf(
                credential.relyingPartyId(),
                credential.originHost(),
                currentRpId,
                currentHost);
        String deviceName = hasText(credential.deviceName()) ? credential.deviceName() : "Passkey sem nome";
        String credentialRef = credential.credentialId() == null
                ? null
                : LogSanitizer.fingerprint(credential.credentialId());

        return new PasskeyDeviceDTO(
                credentialRef,
                deviceName,
                credential.brand(),
                credential.model(),
                credential.serialNumber(),
                credential.deviceInstallId(),
                credential.platform(),
                credential.browser(),
                credential.firstAccessAt(),
                credential.lastAccessAt(),
                credential.status(),
                credential.relyingPartyId(),
                credential.originHost(),
                compatibility.name(),
                compatibility == CompatibilityStatus.COMPATIBLE);
    }

    private CompatibilityStatus compatibilityOf(
            String relyingPartyId,
            String originHost,
            String currentRpId,
            String currentHost) {
        if (!hasText(currentRpId) && !hasText(currentHost)) {
            return CompatibilityStatus.UNKNOWN;
        }

        boolean hasRpIdMetadata = hasText(relyingPartyId);
        boolean hasOriginMetadata = hasText(originHost);
        if (!hasRpIdMetadata && !hasOriginMetadata) {
            return CompatibilityStatus.UNKNOWN;
        }

        if (matches(relyingPartyId, currentRpId)
                || matches(relyingPartyId, currentHost)
                || matches(originHost, currentHost)) {
            return CompatibilityStatus.COMPATIBLE;
        }

        // Application-scoped mobile RP (e.g. kerosene-device) with legacy null/blank originHost
        // was historically stored when android: origins failed URI host parsing.
        // If RP ids match the configured application RP, treat as compatible.
        if (hasRpIdMetadata
                && matches(relyingPartyId, currentRpId)
                && isApplicationScopedRp(currentRpId)) {
            return CompatibilityStatus.COMPATIBLE;
        }
        if (hasRpIdMetadata
                && isApplicationScopedRp(relyingPartyId)
                && isApplicationScopedRp(currentRpId)
                && matches(relyingPartyId, currentRpId)) {
            return CompatibilityStatus.COMPATIBLE;
        }
        // Android origin tokens (apk-key-hash:...) vs HTTP request host should not mark incompatible
        // when relying party already matched application scope above; remaining mismatch stays INCOMPATIBLE.
        if (isAndroidOriginToken(originHost) && isApplicationScopedRp(relyingPartyId)
                && isApplicationScopedRp(currentRpId)) {
            return CompatibilityStatus.COMPATIBLE;
        }

        return CompatibilityStatus.INCOMPATIBLE;
    }

    private boolean isApplicationScopedRp(String rpId) {
        if (!hasText(rpId)) {
            return false;
        }
        String normalized = rpId.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.contains(".") && !normalized.contains(":");
    }

    private boolean isAndroidOriginToken(String originHost) {
        if (!hasText(originHost)) {
            return false;
        }
        String normalized = originHost.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("apk-key-hash:") || normalized.startsWith("android:");
    }

    private boolean shouldLinkNewPasskey(UserDataBase user, PasskeyInventoryDTO inventory) {
        return user.hasTotpEnabled()
                && !inventory.compatibleForCurrentLogin()
                && !inventory.legacyCredentialsPresent();
    }

    private String guidanceFor(UserDataBase user, PasskeyInventoryDTO inventory, boolean canRetryWithChallenge) {
        if (shouldLinkNewPasskey(user, inventory)) {
            return "A passkey atual nao atende o login deste dispositivo. Entre com senha + TOTP e vincule uma nova passkey.";
        }
        if (canRetryWithChallenge) {
            return "Assine o challenge com uma passkey vinculada a este login para concluir a operacao.";
        }
        return "Use uma passkey registrada neste login ou vincule outra passkey compatível.";
    }

    private boolean matches(String left, String right) {
        return hasText(left) && hasText(right) && left.equalsIgnoreCase(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum CompatibilityStatus {
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }
}
