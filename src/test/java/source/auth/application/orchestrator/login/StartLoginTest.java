package source.auth.application.orchestrator.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import source.auth.AuthExceptions;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;

class StartLoginTest {

    private LoginVerifier verifier;
    private RedisServicer redisService;
    private LoginThrottlePolicy throttlePolicy;
    private StartLogin startLogin;

    @BeforeEach
    void setUp() {
        verifier = mock(LoginVerifier.class);
        redisService = mock(RedisServicer.class);
        throttlePolicy = mock(LoginThrottlePolicy.class);
        startLogin = new StartLogin(verifier, redisService, throttlePolicy);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startShouldCreatePreAuthTokenAndClearLoginFailures() {
        UserDTO dto = new UserDTO();
        dto.setUsername("Alice");

        UserDataBase user = new UserDataBase();
        user.setUsername("alice");
        when(verifier.matcherWithoutDevice(dto)).thenReturn(user);

        String token = startLogin.start(dto);

        assertFalse(token.isBlank());
        verify(throttlePolicy).ensureLoginAllowed("alice");
        verify(redisService).setValue(eq(StartLogin.preAuthKey(token)), eq("alice"), eq(StartLogin.PRE_AUTH_TTL_SECONDS));
        verify(throttlePolicy).clearLoginFailures("alice");
    }

    @Test
    void startShouldRecordLoginFailureWhenCredentialsAreInvalid() {
        UserDTO dto = new UserDTO();
        dto.setUsername("Alice");
        AuthExceptions.InvalidCredentials failure = new AuthExceptions.InvalidCredentials("bad credentials");
        when(verifier.matcherWithoutDevice(dto)).thenThrow(failure);

        AuthExceptions.InvalidCredentials thrown = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> startLogin.start(dto));

        assertEquals(failure, thrown);
        verify(throttlePolicy).recordLoginFailure("alice");
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong());
    }
}
