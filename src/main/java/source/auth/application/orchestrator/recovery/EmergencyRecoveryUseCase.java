package source.auth.application.orchestrator.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import source.auth.application.service.recovery.RecoveryCredentialRotator;
import source.auth.application.service.recovery.RecoveryCredentialRotator.RotationResult;
import source.auth.application.service.recovery.RecoverySecretProtector;
import source.auth.application.service.recovery.RecoverySecretProtector.PreparedRecoverySecrets;
import source.auth.application.service.recovery.RecoveryStateStore;
import source.auth.application.service.recovery.RecoveryStateStore.StoredRecoverySession;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;
import source.auth.application.service.recovery.start.chain.EmergencyRecoveryStartChain;
import source.auth.dto.EmergencyRecoveryFinishRequest;
import source.auth.dto.EmergencyRecoveryFinishResponse;
import source.auth.dto.EmergencyRecoveryStartRequest;
import source.auth.dto.EmergencyRecoveryStartResponse;
import source.auth.dto.EmergencyRecoveryState;

@Component
public class EmergencyRecoveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmergencyRecoveryUseCase.class);

    private final EmergencyRecoveryStartChain recoveryStartChain;
    private final RecoverySecretProtector secretProtector;
    private final RecoveryStateStore stateStore;
    private final RecoveryCredentialRotator credentialRotator;

    @Value("${auth.recovery.required-backup-codes:3}")
    private int requiredRecoveryCodes;

    public EmergencyRecoveryUseCase(EmergencyRecoveryStartChain recoveryStartChain,
            RecoverySecretProtector secretProtector,
            RecoveryStateStore stateStore,
            RecoveryCredentialRotator credentialRotator) {
        this.recoveryStartChain = recoveryStartChain;
        this.secretProtector = secretProtector;
        this.stateStore = stateStore;
        this.credentialRotator = credentialRotator;
    }

    public EmergencyRecoveryStartResponse start(EmergencyRecoveryStartRequest request, String clientFingerprint) {
        EmergencyRecoveryStartContext context = recoveryStartChain.handle(request, clientFingerprint);
        PreparedRecoverySecrets secrets = secretProtector.prepare(context.normalizedUsername(), request.getNewPassphrase());
        StoredRecoverySession session = stateStore.createSession(
                context,
                secrets.hashedPassphrase(),
                secrets.encryptedTotpSecret());

        log.warn("[Recovery] Emergency recovery initiated for username={} using {} recovery codes.",
                context.normalizedUsername(), context.matchedRecoveryCodeHashes().size());

        return new EmergencyRecoveryStartResponse(
                session.sessionId(),
                secrets.otpUri(),
                session.passkeyChallenge(),
                session.expiresInSeconds(),
                requiredRecoveryCodes);
    }

    @Transactional
    public EmergencyRecoveryFinishResponse finish(EmergencyRecoveryFinishRequest request) {
        credentialRotator.validateFinishRequest(request);

        EmergencyRecoveryState state = stateStore.consumeRequired(request.getRecoverySessionId());
        String totpSecret = secretProtector.recoverTotpSecret(state.getEncryptedTotpSecret());
        RotationResult result = credentialRotator.rotate(state, request, totpSecret);

        log.warn("[Recovery] Emergency recovery finished for username={}. Old credentials rotated.", result.username());
        return new EmergencyRecoveryFinishResponse(result.username(), result.newBackupCodes());
    }

    public static String buildClientFingerprint(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String clientIp = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String raw = clientIp + "|" + (userAgent == null ? "-" : userAgent);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return raw.replaceAll("[^a-zA-Z0-9_.:-]", "_");
        }
    }
}
