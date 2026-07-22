package source.auth.application.orchestrator.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import source.auth.AuthExceptions;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;

class LoginThrottlePolicyTest {

    private RedisServicer redisService;
    private UserServiceContract userService;
    private LoginThrottlePolicy policy;

    @BeforeEach
    void setUp() {
        redisService = mock(RedisServicer.class);
        userService = mock(UserServiceContract.class);
        policy = new LoginThrottlePolicy(redisService, userService);
    }

    @Test
    void ensureLoginAllowedShouldBlockAfterFiveFailures() {
        when(redisService.getValue("login_failures:alice")).thenReturn("5");

        AuthExceptions.InvalidCredentials thrown = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> policy.ensureLoginAllowed("alice"));

        assertEquals("Muitas tentativas falhas. Conta bloqueada por 15 minutos.", thrown.getMessage());
    }

    @Test
    void recordSecondFactorFailureShouldPersistCounterAndBlockAfterThreeAttempts() {
        UserDataBase user = new UserDataBase();
        user.setFailedLoginAttempts(2);
        when(redisService.getValue("totp_attempts:alice")).thenReturn("3");

        policy.recordSecondFactorFailure("alice", user);

        assertEquals(3, user.getFailedLoginAttempts());
        verify(redisService).increment("totp_attempts:alice");
        verify(userService).createUserInDataBase(user);
        verify(redisService).setValue("totp_block:alice", "BLOCKED", 300L);
        verify(redisService).deleteValue("totp_attempts:alice");
    }

    @Test
    void recordSecondFactorSuccessShouldClearAttemptsAndResetPersistedFailures() {
        UserDataBase user = new UserDataBase();
        user.setFailedLoginAttempts(4);

        policy.recordSecondFactorSuccess("alice", user);

        assertEquals(0, user.getFailedLoginAttempts());
        verify(redisService).deleteValue("totp_attempts:alice");
        verify(userService).createUserInDataBase(user);
    }
}
