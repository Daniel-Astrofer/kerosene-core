package source.auth.application.service.recovery;

import java.security.SecureRandom;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.redis.contracts.RedisContract;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;
import source.auth.dto.EmergencyRecoveryState;

@Service
public class RecoveryStateStore {

    private final RedisContract redisContract;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.recovery.session-ttl-minutes:10}")
    private long recoverySessionTtlMinutes;

    public RecoveryStateStore(RedisContract redisContract) {
        this.redisContract = redisContract;
    }

    public StoredRecoverySession createSession(EmergencyRecoveryStartContext context, String hashedPassphrase,
            String encryptedTotpSecret) {
        String recoverySessionId = UUID.randomUUID().toString().replace("-", "");
        String passkeyChallenge = generateRecoveryChallenge();

        EmergencyRecoveryState state = new EmergencyRecoveryState();
        state.setSessionId(recoverySessionId);
        state.setUsername(context.normalizedUsername());
        state.setHashedPassphrase(hashedPassphrase);
        state.setEncryptedTotpSecret(encryptedTotpSecret);
        state.setPasskeyChallenge(passkeyChallenge);
        state.setMatchedBackupCodeHashes(context.matchedRecoveryCodeHashes());

        redisContract.saveEmergencyRecoveryState(recoverySessionId, state, recoverySessionTtlMinutes);
        return new StoredRecoverySession(recoverySessionId, passkeyChallenge, recoverySessionTtlMinutes * 60L);
    }

    public EmergencyRecoveryState consumeRequired(String recoverySessionId) {
        EmergencyRecoveryState state = redisContract.getdelEmergencyRecoveryState(recoverySessionId);
        if (state == null) {
            throw new AuthExceptions.RecoverySessionExpiredException(
                    "Recovery session expired or was already consumed. Restart the recovery flow.");
        }
        return state;
    }

    private String generateRecoveryChallenge() {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        StringBuilder sb = new StringBuilder();
        for (byte b : challenge) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record StoredRecoverySession(String sessionId, String passkeyChallenge, long expiresInSeconds) {
    }
}
