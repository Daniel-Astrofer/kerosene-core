package source.auth.application.service.recovery;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthPasskeyGateway;
import source.auth.application.port.out.AuthUserGateway;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.EmergencyRecoveryFinishRequest;
import source.auth.dto.EmergencyRecoveryState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;

import java.util.Map;

@Service
public class RecoveryCredentialRotator {

    private final TOTPVerifier totpVerifier;
    private final PasskeyService passkeyService;
    private final AuthUserGateway userGateway;
    private final AuthPasskeyGateway passkeyGateway;
    private final RecoveryCodeService recoveryCodeService;
    private final NotificationService notificationService;

    public RecoveryCredentialRotator(TOTPVerifier totpVerifier,
            PasskeyService passkeyService,
            AuthUserGateway userGateway,
            AuthPasskeyGateway passkeyGateway,
            RecoveryCodeService recoveryCodeService,
            NotificationService notificationService) {
        this.totpVerifier = totpVerifier;
        this.passkeyService = passkeyService;
        this.userGateway = userGateway;
        this.passkeyGateway = passkeyGateway;
        this.recoveryCodeService = recoveryCodeService;
        this.notificationService = notificationService;
    }

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

    public RotationResult rotate(EmergencyRecoveryState state, EmergencyRecoveryFinishRequest request,
            String totpSecret) {
        validateFinishRequest(request);

        UserDataBase user = userGateway.findByUsername(state.getUsername());
        if (user == null) {
            throw new AuthExceptions.RecoveryRejectedException("Recovery request rejected.");
        }

        if (user.getBackupCodes() == null
                || state.getMatchedBackupCodeHashes() == null
                || !user.getBackupCodes().containsAll(state.getMatchedBackupCodeHashes())) {
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
        passkeyGateway.save(buildPasskeyCredential(user, request, publicKeyBytes));

        notificationService.notifyUser(
                user.getId(),
                NotificationKind.SECURITY_RECOVERY_COMPLETED,
                NotificationSeverity.WARNING,
                "Emergency recovery completed",
                "Your passphrase, TOTP, passkey and recovery codes were rotated. Login again with the new credentials.",
                "/settings",
                "user",
                String.valueOf(user.getId()),
                Map.of("username", user.getUsername()));

        return new RotationResult(user.getUsername(), newBackupCodes.rawCodes());
    }

    private PasskeyCredential buildPasskeyCredential(UserDataBase user, EmergencyRecoveryFinishRequest request,
            byte[] publicKeyBytes) {
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(user);
        credential.setDeviceName(request.getDeviceName());
        credential.setPublicKeyCose(publicKeyBytes);
        credential.setSignatureCount(passkeyService.extractSignatureCount(request.getAuthData()));
        credential.setRelyingPartyId(passkeyService.resolveRelyingPartyIdFromClientData(request.getClientDataJSON()));
        credential.setOriginHost(passkeyService.extractOriginHostFromClientData(request.getClientDataJSON()));

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

    public record RotationResult(String username, List<String> newBackupCodes) {
    }
}
