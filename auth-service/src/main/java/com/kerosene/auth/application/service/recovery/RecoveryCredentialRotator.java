package com.kerosene.auth.application.service.recovery;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.port.out.AuthPasskeyGateway;
import com.kerosene.auth.application.port.out.AuthUserGateway;
import com.kerosene.auth.application.service.devicebinding.DeviceBindingPolicy;
import com.kerosene.auth.dto.devicebinding.DeviceAlreadyBoundDTO;
import com.kerosene.auth.application.service.passkey.PasskeyService;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPVerifier;
import com.kerosene.auth.dto.EmergencyRecoveryFinishRequest;
import com.kerosene.auth.dto.EmergencyRecoveryState;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.notification.l10n.NotificationMessageKey;
import com.kerosene.notification.l10n.NotificationMessages;
import com.kerosene.notification.model.NotificationKind;
import com.kerosene.notification.model.NotificationSeverity;
import com.kerosene.notification.service.NotificationService;

import java.util.Map;

/** Validates recovery completion proof and atomically replaces account authentication credentials. */
@Service
public class RecoveryCredentialRotator {

    /** Verifies a fresh code from the replacement authenticator. */
    private final TOTPVerifier totpVerifier;
    /** Verifies passkey enrollment proof and derives RP/origin metadata. */
    private final PasskeyService passkeyService;
    /** Loads and updates account credentials. */
    private final AuthUserGateway userGateway;
    /** Lists, deletes, and saves account passkey credentials. */
    private final AuthPasskeyGateway passkeyGateway;
    /** Generates the replacement one-time recovery-code set. */
    private final RecoveryCodeService recoveryCodeService;
    /** Sends a security warning after credentials are rotated. */
    private final NotificationService notificationService;
    /** Enforces one-account-per-device installation binding before the new credential is saved. */
    private final DeviceBindingPolicy deviceBindingPolicy;

    /** Creates the credential rotation service from proof, persistence, policy, and notification boundaries. */
    /** @param totpVerifier fresh authenticator code verifier */
    /** @param passkeyService replacement passkey proof verifier */
    /** @param userGateway account persistence port */
    /** @param passkeyGateway passkey persistence port */
    /** @param recoveryCodeService replacement code generator */
    /** @param notificationService recovery-completed notification service */
    /** @param deviceBindingPolicy device installation ownership policy */
    public RecoveryCredentialRotator(TOTPVerifier totpVerifier,
            PasskeyService passkeyService,
            AuthUserGateway userGateway,
            AuthPasskeyGateway passkeyGateway,
            RecoveryCodeService recoveryCodeService,
            NotificationService notificationService,
            DeviceBindingPolicy deviceBindingPolicy) {
        this.totpVerifier = totpVerifier;
        this.passkeyService = passkeyService;
        this.userGateway = userGateway;
        this.passkeyGateway = passkeyGateway;
        this.recoveryCodeService = recoveryCodeService;
        this.notificationService = notificationService;
        this.deviceBindingPolicy = deviceBindingPolicy;
    }

    /** Requires a session ID, a fresh TOTP code, and complete replacement passkey proof/metadata. */
    /** @param request recovery completion request */
    /** @throws IllegalArgumentException when required session or proof fields are missing */
    public void validateFinishRequest(EmergencyRecoveryFinishRequest request) {
        if (request == null || request.getRecoverySessionId() == null || request.getRecoverySessionId().isBlank()) {
            throw new IllegalArgumentException("Recovery sessionId is required.");
        }
        if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
            throw new IllegalArgumentException("A fresh TOTP code from the new authenticator is required.");
        }
        if (request.getSignature() == null || request.getSignature().isBlank()
                || request.getAuthData() == null || request.getAuthData().isBlank()
                || request.getClientDataJSON() == null || request.getClientDataJSON().isBlank()
                || request.getCredentialId() == null || request.getCredentialId().isBlank()
                || request.getDeviceName() == null || request.getDeviceName().isBlank()) {
            throw new IllegalArgumentException("A new passkey proof is required to complete recovery.");
        }
    }

    /**
     * Revalidates one-time recovery-code ownership, verifies new TOTP/passkey proof, rotates password,
     * TOTP and backup codes, removes old passkeys, claims the device installation and notifies the owner.
     *
     * @param state consumed recovery session containing replacement secrets and matched old code hashes
     * @param request completion proof and replacement passkey metadata
     * @param totpSecret decrypted replacement TOTP seed
     * @return account name and raw replacement codes for one-time display
     */
    public RotationResult rotate(EmergencyRecoveryState state, EmergencyRecoveryFinishRequest request,
            String totpSecret) {
        validateFinishRequest(request);

        UserDataBase user = userGateway.findByUsername(state.getUsername());
        if (user == null) {
            throw new AuthExceptions.RecoveryRejectedException("Recovery request rejected.");
        }

        if (user.getBackupCodes() == null
                || state.getMatchedBackupCodeHashes() == null
                || !new HashSet<>(user.getBackupCodes()).containsAll(state.getMatchedBackupCodeHashes())) {
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. Existing recovery codes were already rotated.");
        }

        if (!totpVerifier.totpMatcher(totpSecret, request.getTotpCode())) {
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. The new authenticator proof was invalid.");
        }

        byte[] publicKeyBytes = decodePasskeyPublicKey(request);
        if (!passkeyService.verifyRegistrationSignature(
                state.getUsername(),
                state.getPasskeyChallenge(),
                request.getSignature(),
                publicKeyBytes,
                request.getAuthData(),
                request.getClientDataJSON())) {
            throw new AuthExceptions.RecoveryRejectedException(
                    "Recovery request rejected. The new passkey proof was invalid.");
        }

        user.setPassphrase(state.getHashedPassphrase());
        user.setTOTPSecret(totpSecret);
        user.setFailedLoginAttempts(0);

        RecoveryCodeService.GeneratedRecoveryCodes newBackupCodes = recoveryCodeService.generateNewBackupCodes();
        user.setBackupCodes(newBackupCodes.hashedCodes());
        userGateway.save(user);

        List<PasskeyCredential> existingCredentials = passkeyGateway.findByUserId(user.getId());
        if (existingCredentials != null && !existingCredentials.isEmpty()) {
            passkeyGateway.deleteAll(existingCredentials);
        }

        // One device → one account: claim this install for the recovering user (may DELETE other accounts' keys on it).
        DeviceAlreadyBoundDTO bindingConflict = deviceBindingPolicy.ensureDeviceAvailableForBind(
                request.getDeviceInstallId(),
                user.getId(),
                request.isConfirmUnlinkDevice());
        if (bindingConflict != null) {
            throw new AuthExceptions.RecoveryRejectedException(
                    bindingConflict.message() != null
                            ? bindingConflict.message()
                            : "This device is bound to another account. Confirm unlink to continue recovery.");
        }

        passkeyGateway.save(buildPasskeyCredential(user, request, publicKeyBytes));

        notificationService.notifyUser(
                user.getId(),
                NotificationMessages.payload(
                        NotificationKind.SECURITY_RECOVERY_COMPLETED,
                        NotificationSeverity.WARNING,
                        NotificationMessageKey.SECURITY_RECOVERY_COMPLETED,
                        "/settings",
                        "user",
                        String.valueOf(user.getId()),
                        Map.of("username", user.getUsername())));

        return new RotationResult(user.getUsername(), newBackupCodes.rawCodes());
    }

    /** Builds the persisted passkey entity from verified proof and client/device metadata. */
    /** @param user account receiving the new credential */
    /** @param request verified completion request */
    /** @param publicKeyBytes decoded public key material */
    /** @return active passkey credential with counter and proof-derived RP/origin */
    private PasskeyCredential buildPasskeyCredential(UserDataBase user, EmergencyRecoveryFinishRequest request,
            byte[] publicKeyBytes) {
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(user);
        credential.setDeviceName(request.getDeviceName());
        credential.setPublicKeyCose(publicKeyBytes);
        credential.setSignatureCount(passkeyService.extractSignatureCount(request.getAuthData()));
        credential.setRelyingPartyId(resolveRelyingPartyIdFromProof(request));
        credential.setOriginHost(passkeyService.extractOriginHostFromClientData(request.getClientDataJSON()));
        credential.setDeviceInstallId(request.getDeviceInstallId());
        credential.setBrand(request.getBrand());
        credential.setModel(request.getModel());
        credential.setSerialNumber(request.getSerialNumber());
        credential.setPlatform(request.getPlatform());
        credential.setBrowser(request.getBrowser());
        credential.setStatus("ACTIVE");

        Base64.Decoder decoder = Base64.getDecoder();
        try {
            byte[] credentialId = decoder.decode(request.getCredentialId());
            credential.setCredentialId(credentialId);
            if (request.getUserHandle() != null && !request.getUserHandle().isBlank()) {
                credential.setUserHandle(decoder.decode(request.getUserHandle()));
            } else {
                credential.setUserHandle(credentialId);
            }
        } catch (IllegalArgumentException e) {
            decoder = Base64.getUrlDecoder();
            byte[] credentialId = decoder.decode(request.getCredentialId());
            credential.setCredentialId(credentialId);
            if (request.getUserHandle() != null && !request.getUserHandle().isBlank()) {
                credential.setUserHandle(decoder.decode(request.getUserHandle()));
            } else {
                credential.setUserHandle(credentialId);
            }
        }

        return credential;
    }

    /** Decodes COSE key material when present, otherwise legacy publicKey material, accepting both Base64 alphabets. */
    /** @param request recovery completion request */
    /** @return decoded public key bytes */
    /** @throws IllegalArgumentException when required key data is absent or malformed */
    private byte[] decodePasskeyPublicKey(EmergencyRecoveryFinishRequest request) {
        String keyToDecode = request.getPublicKeyCose() != null && !request.getPublicKeyCose().isBlank()
                ? request.getPublicKeyCose()
                : request.getPublicKey();
        if (keyToDecode == null || keyToDecode.isBlank()) {
            throw new IllegalArgumentException("publicKeyCose or publicKey is required.");
        }

        try {
            return Base64.getDecoder().decode(keyToDecode);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(keyToDecode);
        }
    }

    /** Resolves the RP identifier from authenticator proof, falling back to client-data context. */
    /** @param request completion request containing proof fields */
    /** @return matched or inferred relying-party identifier */
    private String resolveRelyingPartyIdFromProof(EmergencyRecoveryFinishRequest request) {
        String matchedRpId = passkeyService.resolveRelyingPartyIdFromAuthenticatorData(
                request.getAuthData(),
                request.getClientDataJSON());
        if (matchedRpId != null && !matchedRpId.isBlank()) {
            return matchedRpId;
        }
        return passkeyService.resolveRelyingPartyIdFromClientData(request.getClientDataJSON());
    }

    /**
     * Successful credential rotation result returned to the recovery use case.
     * @param username account whose credentials were rotated
     * @param newBackupCodes raw replacement codes shown once
     */
    public record RotationResult(String username, List<String> newBackupCodes) {
    }
}
