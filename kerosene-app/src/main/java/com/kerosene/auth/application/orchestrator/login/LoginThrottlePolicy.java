package source.auth.application.orchestrator.login;

import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;

@Component
public class LoginThrottlePolicy {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_FAILURE_TTL_SECONDS = 15 * 60L;
    private static final int MAX_SECOND_FACTOR_ATTEMPTS = 3;
    private static final long SECOND_FACTOR_BLOCK_TTL_SECONDS = 300L;
    private static final int MAX_PERSISTED_FAILED_ATTEMPTS = 10;

    private final RedisServicer redisService;
    private final UserServiceContract userService;

    public LoginThrottlePolicy(RedisServicer redisService,
            UserServiceContract userService) {
        this.redisService = redisService;
        this.userService = userService;
    }

    public void ensureLoginAllowed(String username) {
        if (readCounter(loginFailuresKey(username)) >= MAX_LOGIN_FAILURES) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. Conta bloqueada por 15 minutos.");
        }
    }

    public void recordLoginFailure(String username) {
        redisService.increment(loginFailuresKey(username));
        redisService.expire(loginFailuresKey(username), LOGIN_FAILURE_TTL_SECONDS);
    }

    public void clearLoginFailures(String username) {
        redisService.deleteValue(loginFailuresKey(username));
    }

    public void ensureSecondFactorAllowed(String username) {
        if (redisService.getValue(secondFactorBlockKey(username)) != null) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. TOTP bloqueado por 5 minutos.");
        }
    }

    public void ensureEmergencyTotpAllowed(UserDataBase user) {
        if (user.getFailedLoginAttempts() >= MAX_PERSISTED_FAILED_ATTEMPTS) {
            throw new AuthExceptions.InvalidCredentials(
                    "Conta bloqueada emergencialmente por segurança. O uso do TOTP foi desativado. Resgate manual necessário.");
        }
    }

    public void recordSecondFactorSuccess(String username, UserDataBase user) {
        redisService.deleteValue(secondFactorAttemptsKey(username));
        user.setFailedLoginAttempts(0);
        userService.createUserInDataBase(user);
    }

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

    private int readCounter(String key) {
        return readCounter(key, 0);
    }

    private int readCounter(String key, int defaultValue) {
        String value = redisService.getValue(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private String loginFailuresKey(String username) {
        return "login_failures:" + username;
    }

    private String secondFactorBlockKey(String username) {
        return "totp_block:" + username;
    }

    private String secondFactorAttemptsKey(String username) {
        return "totp_attempts:" + username;
    }
}
