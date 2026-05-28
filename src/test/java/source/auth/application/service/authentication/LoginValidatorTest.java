package source.auth.application.service.authentication;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthUserGateway;
import source.auth.application.service.authentication.login.LoginCredentialRules;
import source.auth.application.service.authentication.login.chain.LoginPassphraseVerificationHandler;
import source.auth.application.service.authentication.login.chain.LoginRateLimitHandler;
import source.auth.application.service.authentication.login.chain.LoginRequiredFieldsHandler;
import source.auth.application.service.authentication.login.chain.LoginUserLookupHandler;
import source.auth.application.service.authentication.login.chain.LoginValidationChain;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;

class LoginValidatorTest {

    private AuthUserGateway userGateway;
    private Hasher hasher;
    private RedisServicer redisService;
    private LoginValidator validator;

    @BeforeEach
    void setUp() {
        userGateway = org.mockito.Mockito.mock(AuthUserGateway.class);
        hasher = org.mockito.Mockito.mock(Hasher.class);
        redisService = org.mockito.Mockito.mock(RedisServicer.class);

        LoginCredentialRules rules = new LoginCredentialRules(userGateway, hasher, redisService);
        LoginValidationChain chain = new LoginValidationChain(List.of(
                new LoginPassphraseVerificationHandler(rules),
                new LoginRequiredFieldsHandler(rules),
                new LoginUserLookupHandler(rules),
                new LoginRateLimitHandler(rules)));
        validator = new LoginValidator(rules, chain);
    }

    @Test
    void matcherWithoutDeviceShouldAuthenticateAndClearRateLimiter() {
        UserDTO dto = new UserDTO();
        dto.setUsername(" Alice ");
        dto.setPassphrase("  legal   winner  ".toCharArray());

        UserDataBase user = new UserDataBase();
        user.setUsername("alice");
        user.setPassphrase("stored-hash");

        when(redisService.increment("rl:login:alice")).thenReturn(1L);
        when(userGateway.findByUsername("alice")).thenReturn(user);
        when(hasher.verify(any(char[].class), eq("stored-hash"))).thenAnswer(invocation -> {
            char[] candidate = invocation.getArgument(0, char[].class);
            assertEquals("legal winner", new String(candidate));
            return true;
        });

        UserDataBase authenticated = validator.matcherWithoutDevice(dto);

        assertSame(user, authenticated);
        verify(redisService).expire("rl:login:alice", 60);
        verify(redisService).deleteValue("rl:login:alice");
        assertAll(
                () -> assertEquals('\0', dto.getPassphrase()[0]),
                () -> assertEquals('\0', dto.getPassphrase()[dto.getPassphrase().length - 1]));
    }

    @Test
    void matcherWithoutDeviceShouldRejectWhenRateLimitIsExceeded() {
        UserDTO dto = new UserDTO();
        dto.setUsername("alice");
        dto.setPassphrase("secret".toCharArray());

        when(redisService.increment("rl:login:alice")).thenReturn(6L);

        assertThrows(AuthExceptions.InvalidCredentials.class, () -> validator.matcherWithoutDevice(dto));
        verify(userGateway, never()).findByUsername(any());
        verify(redisService, never()).deleteValue("rl:login:alice");
    }

    @Test
    void findByUsernameOnlyShouldNormalizeUsernameBeforeLookup() {
        UserDataBase user = new UserDataBase();
        user.setUsername("alice");
        when(userGateway.findByUsername("alice")).thenReturn(user);

        UserDataBase loaded = validator.findByUsernameOnly(" Alice ");

        assertSame(user, loaded);
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        verify(userGateway).findByUsername(usernameCaptor.capture());
        assertEquals("alice", usernameCaptor.getValue());
    }
}
