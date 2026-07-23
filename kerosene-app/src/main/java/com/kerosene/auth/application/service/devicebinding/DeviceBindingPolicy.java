package com.kerosene.auth.application.service.devicebinding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import com.kerosene.auth.dto.devicebinding.DeviceAlreadyBoundDTO;
import com.kerosene.auth.model.entity.DeviceKeyCredential;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.common.infra.logging.LogDomain;
import com.kerosene.common.infra.logging.LogSanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Unified device↔account binding policy for passkey and device-key credentials.
 *
 * <p>Rules:
 * <ul>
 *   <li>One device ({@code deviceInstallId}) may be bound to at most one account.</li>
 *   <li>One account may have many devices.</li>
 *   <li>Rebinding a device to a different account requires explicit {@code confirmUnlinkDevice}
 *       and deletes the previous account's credentials for that install from the database.</li>
 * </ul>
 */
@Service
public class DeviceBindingPolicy {

    private static final Logger log = LoggerFactory.getLogger(DeviceBindingPolicy.class);

    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final DeviceKeyCredentialRepository deviceKeyCredentialRepository;

    public DeviceBindingPolicy(
            PasskeyCredentialRepository passkeyCredentialRepository,
            DeviceKeyCredentialRepository deviceKeyCredentialRepository) {
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.deviceKeyCredentialRepository = deviceKeyCredentialRepository;
    }

    /**
     * Read-only conflict probe. Runs outside any outer write transaction so callers can return
     * AUTH_024 without marking the outer TX rollback-only.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public DeviceAlreadyBoundDTO findBindingConflict(String deviceInstallId, Long targetUserId) {
        String installId = normalizeInstallId(deviceInstallId);
        if (installId == null) {
            return null;
        }
        DeviceOwner owner = findActiveOwner(installId);
        if (owner == null) {
            return null;
        }
        if (targetUserId != null && Objects.equals(owner.userId(), targetUserId)) {
            return null;
        }
        return DeviceAlreadyBoundDTO.of(installId, maskUsername(owner.username()));
    }

    /**
     * Ensures the install can be bound to {@code targetUserId} (null during signup before user exists).
     * When another account owns the install and {@code confirmUnlinkDevice} is false, returns a
     * {@link DeviceAlreadyBoundDTO} without throwing (avoids Spring rollback-only on checked conflict).
     * When true, deletes all credentials on that install that do not belong to the target user
     * (or all credentials when target is null / signup).
     *
     * @return null if binding may proceed; otherwise the conflict payload for AUTH_024
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeviceAlreadyBoundDTO ensureDeviceAvailableForBind(
            String deviceInstallId,
            Long targetUserId,
            boolean confirmUnlinkDevice) {
        String installId = normalizeInstallId(deviceInstallId);
        if (installId == null) {
            return null;
        }

        DeviceOwner owner = findActiveOwner(installId);
        if (owner == null) {
            return null;
        }

        if (targetUserId != null && Objects.equals(owner.userId(), targetUserId)) {
            // Same account re-registering on this device: replace credentials for this install only.
            deleteCredentialsForInstallExceptUser(installId, targetUserId);
            deleteCredentialsForUserAndInstall(installId, targetUserId);
            return null;
        }

        // Owned by another account (or any account during pre-user signup).
        if (!confirmUnlinkDevice) {
            return DeviceAlreadyBoundDTO.of(installId, maskUsername(owner.username()));
        }

        int removed = deleteAllCredentialsForInstall(installId);
        log.info(
                LogDomain.AUTH,
                "event=DEVICE_BINDING_UNLINK deviceInstallRef={} previousUserRef={} removedCredentials={} targetUserRef={}",
                LogSanitizer.fingerprint(installId),
                LogSanitizer.fingerprint(owner.username()),
                removed,
                targetUserId == null ? "signup" : LogSanitizer.fingerprint(String.valueOf(targetUserId)));
        return null;
    }

    /**
     * After recovery rotation deletes all user passkeys, clear any device-key leftovers for installs
     * that will be rebound — recovery already replaces passkeys; also wipe device-keys for those installs
     * owned only by this user is handled by delete-all passkeys. Call when a new credential is about
     * to claim an install after full user credential wipe.
     */
    @Transactional
    public void releaseInstallForRecovery(String deviceInstallId, Long recoveringUserId) {
        String installId = normalizeInstallId(deviceInstallId);
        if (installId == null) {
            return;
        }
        // Drop foreign bindings so the recovering user can claim the device.
        ensureDeviceAvailableForBind(installId, recoveringUserId, true);
    }

    public DeviceOwner findActiveOwner(String deviceInstallId) {
        String installId = normalizeInstallId(deviceInstallId);
        if (installId == null) {
            return null;
        }

        List<PasskeyCredential> passkeys =
                passkeyCredentialRepository.findActiveByDeviceInstallId(installId);
        for (PasskeyCredential credential : passkeys) {
            if (credential.getUser() != null && isActive(credential.getStatus())) {
                return new DeviceOwner(
                        credential.getUser().getId(),
                        credential.getUser().getUsername());
            }
        }

        List<DeviceKeyCredential> deviceKeys =
                deviceKeyCredentialRepository.findActiveByDeviceInstallId(installId);
        for (DeviceKeyCredential credential : deviceKeys) {
            if (credential.getUser() != null && isActive(credential.getStatus())) {
                return new DeviceOwner(
                        credential.getUser().getId(),
                        credential.getUser().getUsername());
            }
        }
        return null;
    }

    @Transactional
    public int deleteAllCredentialsForInstall(String deviceInstallId) {
        String installId = normalizeInstallId(deviceInstallId);
        if (installId == null) {
            return 0;
        }
        int passkeys = passkeyCredentialRepository.deleteByDeviceInstallId(installId);
        int deviceKeys = deviceKeyCredentialRepository.deleteByDeviceInstallId(installId);
        return passkeys + deviceKeys;
    }

    @Transactional
    public void deleteCredentialsForUserAndInstall(String deviceInstallId, Long userId) {
        String installId = normalizeInstallId(deviceInstallId);
        if (installId == null || userId == null) {
            return;
        }
        passkeyCredentialRepository.deleteByUserIdAndDeviceInstallId(userId, installId);
        deviceKeyCredentialRepository.deleteByUserIdAndDeviceInstallId(userId, installId);
    }

    private void deleteCredentialsForInstallExceptUser(String installId, Long userId) {
        List<PasskeyCredential> passkeys =
                passkeyCredentialRepository.findActiveByDeviceInstallId(installId);
        List<PasskeyCredential> foreignPasskeys = new ArrayList<>();
        for (PasskeyCredential credential : passkeys) {
            if (credential.getUser() == null || !Objects.equals(credential.getUser().getId(), userId)) {
                foreignPasskeys.add(credential);
            }
        }
        if (!foreignPasskeys.isEmpty()) {
            passkeyCredentialRepository.deleteAll(foreignPasskeys);
        }

        List<DeviceKeyCredential> deviceKeys =
                deviceKeyCredentialRepository.findActiveByDeviceInstallId(installId);
        List<DeviceKeyCredential> foreignKeys = new ArrayList<>();
        for (DeviceKeyCredential credential : deviceKeys) {
            if (credential.getUser() == null || !Objects.equals(credential.getUser().getId(), userId)) {
                foreignKeys.add(credential);
            }
        }
        if (!foreignKeys.isEmpty()) {
            deviceKeyCredentialRepository.deleteAll(foreignKeys);
        }
    }

    public static String normalizeInstallId(String deviceInstallId) {
        if (deviceInstallId == null) {
            return null;
        }
        String trimmed = deviceInstallId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String maskUsername(String username) {
        if (username == null || username.isBlank()) {
            return "***";
        }
        String normalized = username.trim();
        if (normalized.length() <= 2) {
            return normalized.charAt(0) + "***";
        }
        if (normalized.length() <= 4) {
            return normalized.substring(0, 1) + "***" + normalized.substring(normalized.length() - 1);
        }
        return normalized.substring(0, 2) + "***" + normalized.substring(normalized.length() - 1);
    }

    private static boolean isActive(String status) {
        return status == null
                || status.isBlank()
                || "ACTIVE".equalsIgnoreCase(status.trim());
    }

    public record DeviceOwner(Long userId, String username) {}
}
