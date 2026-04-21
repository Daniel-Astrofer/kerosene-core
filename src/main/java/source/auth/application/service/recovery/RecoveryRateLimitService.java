package source.auth.application.service.recovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.redis.contracts.RedisContract;

@Service
public class RecoveryRateLimitService {

    private final RedisContract redisContract;

    @Value("${auth.recovery.client-window-seconds:600}")
    private long clientWindowSeconds;

    @Value("${auth.recovery.client-max-attempts:6}")
    private long clientMaxAttempts;

    @Value("${auth.recovery.username-window-seconds:1800}")
    private long usernameWindowSeconds;

    @Value("${auth.recovery.username-max-attempts:4}")
    private long usernameMaxAttempts;

    @Value("${auth.recovery.block-seconds:1800}")
    private long recoveryBlockSeconds;

    public RecoveryRateLimitService(RedisContract redisContract) {
        this.redisContract = redisContract;
    }

    public void enforceStartAttempt(String normalizedUsername, String clientFingerprint) {
        String clientKey = "auth:recovery:attempts:client:" + clientFingerprint;
        String clientBlockKey = "auth:recovery:block:client:" + clientFingerprint;
        String userBlockKey = "auth:recovery:block:user:" + normalizedUsername;

        if (redisContract.getValue(clientBlockKey) != null || redisContract.getValue(userBlockKey) != null) {
            throw new AuthExceptions.RecoveryRateLimitedException(
                    "Emergency recovery is temporarily blocked for this client or username.");
        }

        Long clientAttempts = redisContract.increment(clientKey);
        if (clientAttempts == 1L) {
            redisContract.expire(clientKey, clientWindowSeconds);
        }
        if (clientAttempts > clientMaxAttempts) {
            redisContract.setValue(clientBlockKey, "1", recoveryBlockSeconds);
            throw new AuthExceptions.RecoveryRateLimitedException(
                    "Emergency recovery is temporarily blocked for this client.");
        }
    }

    public void registerFailure(String normalizedUsername, String clientFingerprint) {
        String userAttemptsKey = "auth:recovery:attempts:user:" + normalizedUsername;
        Long userAttempts = redisContract.increment(userAttemptsKey);
        if (userAttempts == 1L) {
            redisContract.expire(userAttemptsKey, usernameWindowSeconds);
        }
        if (userAttempts >= usernameMaxAttempts) {
            redisContract.setValue("auth:recovery:block:user:" + normalizedUsername, "1", recoveryBlockSeconds);
            redisContract.setValue("auth:recovery:block:client:" + clientFingerprint, "1", recoveryBlockSeconds);
        }
    }

    public void clearFailures(String normalizedUsername, String clientFingerprint) {
        redisContract.deleteValue("auth:recovery:attempts:user:" + normalizedUsername);
        redisContract.deleteValue("auth:recovery:block:user:" + normalizedUsername);
        redisContract.deleteValue("auth:recovery:attempts:client:" + clientFingerprint);
        redisContract.deleteValue("auth:recovery:block:client:" + clientFingerprint);
    }
}
