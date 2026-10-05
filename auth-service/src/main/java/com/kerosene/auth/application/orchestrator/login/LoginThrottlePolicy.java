package com.kerosene.auth.application.orchestrator.login;

import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Applies account login and second-factor attempt limits using Redis and persisted user state. */
@Component
public class LoginThrottlePolicy {

    /** Number of consecutive primary credential failures allowed in the rolling Redis window. */
    private static final int MAX_LOGIN_FAILURES = 5;
    /** Lifetime assigned to the primary credential failure counter. */
    private static final long LOGIN_FAILURE_TTL_SECONDS = 15 * 60L;
    /** Number of failed TOTP or backup-code attempts before a temporary block is created. */
    private static final int MAX_SECOND_FACTOR_ATTEMPTS = 3;
    /** Lifetime of the temporary second-factor block. */
    private static final long SECOND_FACTOR_BLOCK_TTL_SECONDS = 300L;
    /** Persisted failed-attempt threshold after which emergency TOTP use is disabled. */
    private static final int MAX_PERSISTED_FAILED_ATTEMPTS = 10;

    /** Redis-backed counters and short-lived second-factor block markers. */
    private final RedisServicer redisService;
    /** Persistence service used to save durable failed-attempt changes to the user. */
    private final UserServiceContract userService;

    /**
     * Creates the policy with shared counter storage and user persistence.
     *
     * @param redisService Redis access for counters and temporary blocks
     * @param userService user persistence boundary
     */
    public LoginThrottlePolicy(RedisServicer redisService,
            UserServiceContract userService) {
        this.redisService = redisService;
        this.userService = userService;
    }

    /** Rejects a username whose primary credential failure count has reached the limit. */
    /** @param username canonicalized username used to scope the counter */
    /** @throws AuthExceptions.InvalidCredentials when the account is currently blocked */
    public void ensureLoginAllowed(String username) {
        if (readCounter(loginFailuresKey(username)) >= MAX_LOGIN_FAILURES) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. Conta bloqueada por 15 minutos.");
        }
    }

    /** Increments the primary login failure counter and refreshes its fifteen-minute expiry. */
    /** @param username canonicalized username */
    public void recordLoginFailure(String username) {
        redisService.increment(loginFailuresKey(username));
        redisService.expire(loginFailuresKey(username), LOGIN_FAILURE_TTL_SECONDS);
    }

    /** Removes the primary login failure counter after successful primary authentication. */
    /** @param username canonicalized username */
    public void clearLoginFailures(String username) {
        redisService.deleteValue(loginFailuresKey(username));
    }

    /** Rejects attempts while a Redis second-factor block marker exists. */
    /** @param username canonicalized username */
    /** @throws AuthExceptions.InvalidCredentials when a temporary TOTP block is active */
    public void ensureSecondFactorAllowed(String username) {
        if (redisService.getValue(secondFactorBlockKey(username)) != null) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. TOTP bloqueado por 5 minutos.");
        }
    }

    /** Rejects emergency TOTP once the durable failed-attempt threshold has been reached. */
    /** @param user account whose persisted failed-attempt count is checked */
    /** @throws AuthExceptions.InvalidCredentials when emergency TOTP has been disabled */
    public void ensureEmergencyTotpAllowed(UserDataBase user) {
        if (user.getFailedLoginAttempts() >= MAX_PERSISTED_FAILED_ATTEMPTS) {
            throw new AuthExceptions.InvalidCredentials(
                    "Conta bloqueada emergencialmente por segurança. O uso do TOTP foi desativado. Resgate manual necessário.");
        }
    }

    /** Clears transient factor attempts and resets the persisted failed-attempt count on success. */
    /** @param username canonicalized username used for the Redis counter */
    /** @param user user entity whose failed-attempt count is reset and saved */
    public void recordSecondFactorSuccess(String username, UserDataBase user) {
        redisService.deleteValue(secondFactorAttemptsKey(username));
        user.setFailedLoginAttempts(0);
        userService.createUserInDataBase(user);
    }

    /**
     * Increments transient and durable failure counts, then blocks after the configured limit.
     * The temporary counter is deleted when the block marker is written.
     *
     * @param username canonicalized username used to scope Redis keys
     * @param user user entity whose durable failed-attempt count is advanced and saved
     */
    public void recordSecondFactorFailure(String username, UserDataBase user) {
        redisService.increment(secondFactorAttemptsKey(username));
        int currentAttempts = readCounter(secondFactorAttemptsKey(username), 1);

        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        userService.createUserInDataBase(user);

        if (currentAttempts >= MAX_SECOND_FACTOR_ATTEMPTS) {
            redisService.setValue(secondFactorBlockKey(username), "BLOCKED", SECOND_FACTOR_BLOCK_TTL_SECONDS);
            redisService.deleteValue(secondFactorAttemptsKey(username));
        }
    }

    /** Reads a Redis counter and uses zero when its key is missing. */
    /** @param key Redis counter key */
    /** @return parsed counter or zero */
    private int readCounter(String key) {
        return readCounter(key, 0);
    }

    /** Reads and parses a Redis counter, substituting the caller's default when absent. */
    /** @param key Redis counter key */
    /** @param defaultValue value used when Redis has no counter */
    /** @return parsed counter or {@code defaultValue} */
    private int readCounter(String key, int defaultValue) {
        String value = redisService.getValue(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    /** Builds the Redis key for primary credential failure counts. */
    /** @param username canonicalized username */
    /** @return namespaced failure counter key */
    private String loginFailuresKey(String username) {
        return "login_failures:" + username;
    }

    /** Builds the Redis key marking a temporary second-factor block. */
    /** @param username canonicalized username */
    /** @return namespaced block marker key */
    private String secondFactorBlockKey(String username) {
        return "totp_block:" + username;
    }

    /** Builds the Redis key for the current second-factor attempt count. */
    /** @param username canonicalized username */
    /** @return namespaced second-factor counter key */
    private String secondFactorAttemptsKey(String username) {
        return "totp_attempts:" + username;
    }
}
