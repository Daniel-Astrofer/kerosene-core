package source.auth.application.service.devicebinding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.common.infra.logging.LogDomain;
import source.common.infra.logging.LogSanitizer;

/**
 * Tracks signature-counter replay failures per credential and applies a short soft-lock
 * after repeated failures (clone / desync signal — not "link a new passkey").
 */
@Service
public class DeviceCredentialReplayGuard {

    private static final Logger log = LoggerFactory.getLogger(DeviceCredentialReplayGuard.class);

    private static final String FAIL_PREFIX = "device_cred_replay_fail:";
    private static final String LOCK_PREFIX = "device_cred_replay_lock:";

    private final RedisServicer redisService;
    private final int failureThreshold;
    private final long failureWindowSeconds;
    private final long lockSeconds;

    public DeviceCredentialReplayGuard(
            RedisServicer redisService,
            @Value("${kerosene.auth.device-cred.replay-failure-threshold:3}") int failureThreshold,
            @Value("${kerosene.auth.device-cred.replay-failure-window-seconds:900}") long failureWindowSeconds,
            @Value("${kerosene.auth.device-cred.replay-lock-seconds:1200}") long lockSeconds) {
        this.redisService = redisService;
        this.failureThreshold = failureThreshold > 0 ? failureThreshold : 3;
        this.failureWindowSeconds = failureWindowSeconds > 0 ? failureWindowSeconds : 900L;
        this.lockSeconds = lockSeconds > 0 ? lockSeconds : 1200L;
    }

    public long lockSeconds() {
        return lockSeconds;
    }

    public int failureThreshold() {
        return failureThreshold;
    }

    public boolean isLocked(Long userId, String credentialRef) {
        if (userId == null || credentialRef == null || credentialRef.isBlank()) {
            return false;
        }
        return redisService.getValue(lockKey(userId, credentialRef)) != null;
    }

    /**
     * @return true if a soft-lock is now active (threshold reached)
     */
    public boolean recordReplayFailure(Long userId, String credentialRef, String factorKind) {
        if (userId == null || credentialRef == null || credentialRef.isBlank()) {
            return false;
        }
        String failKey = failKey(userId, credentialRef);
        Long count = redisService.increment(failKey);
        if (count == null) {
            count = 1L;
        }
        if (count == 1L) {
            redisService.expire(failKey, failureWindowSeconds);
        }
        log.warn(
                LogDomain.AUTH,
                "event=DEVICE_CREDENTIAL_REPLAY_FAILURE userId={} credentialRef={} factor={} count={}/{}",
                userId,
                credentialRef,
                factorKind,
                count,
                failureThreshold);

        if (count >= failureThreshold) {
            redisService.setValue(lockKey(userId, credentialRef), "1", lockSeconds);
            redisService.deleteValue(failKey);
            log.error(
                    LogDomain.AUTH,
                    "event=DEVICE_CREDENTIAL_REPLAY_SOFT_LOCK userId={} credentialRef={} factor={} lockSeconds={}",
                    userId,
                    credentialRef,
                    factorKind,
                    lockSeconds);
            return true;
        }
        return false;
    }

    public void clearFailures(Long userId, String credentialRef) {
        if (userId == null || credentialRef == null || credentialRef.isBlank()) {
            return;
        }
        redisService.deleteValue(failKey(userId, credentialRef));
    }

    public static String credentialRefFromBytes(byte[] credentialId) {
        if (credentialId == null || credentialId.length == 0) {
            return "unknown";
        }
        return LogSanitizer.fingerprint(credentialId);
    }

    public static String credentialRefFromString(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            return "unknown";
        }
        return LogSanitizer.fingerprint(credentialId.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String failKey(Long userId, String credentialRef) {
        return FAIL_PREFIX + userId + ":" + credentialRef;
    }

    private static String lockKey(Long userId, String credentialRef) {
        return LOCK_PREFIX + userId + ":" + credentialRef;
    }
}
