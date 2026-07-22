package source.auth.application.orchestrator.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import source.auth.AuthExceptions;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;

class VerifySecondFactorTest {

    private LoginVerifier verifier;
    private TOTPVerifier totpVerifier;
    private UserServiceContract userService;
    private RedisServicer redisService;
    private Hasher hasher;
    private LoginThrottlePolicy throttlePolicy;
    private VerifySecondFactor verifySecondFactor;

    @BeforeEach
    void setUp() {
        verifier = mock(LoginVerifier.class);
        totpVerifier = mock(TOTPVerifier.class);
        userService = mock(UserServiceContract.class);
        redisService = mock(RedisServicer.class);
        hasher = mock(Hasher.class);
        throttlePolicy = mock(LoginThrottlePolicy.class);
        verifySecondFactor = new VerifySecondFactor(
                verifier,
                totpVerifier,
                userService,
                redisService,
                hasher,
                throttlePolicy);
    }

    @Test
    void verifyShouldAcceptTotpAndDeletePreAuthToken() {
        UserDTO dto = new UserDTO();
        dto.setPreAuthToken("pre-auth");
        dto.setTotpCode("123456");

        UserDataBase user = new UserDataBase();
        user.setUsername("Alice");
        user.setTOTPSecret("secret");

        when(redisService.getValue(StartLogin.preAuthKey("pre-auth"))).thenReturn("Alice");
        when(verifier.findByUsernameOnly("Alice")).thenReturn(user);

        UserDataBase result = verifySecondFactor.verify(dto);

        assertSame(user, result);
        verify(throttlePolicy).ensureSecondFactorAllowed("alice");
        verify(throttlePolicy).ensureEmergencyTotpAllowed(user);
        verify(totpVerifier).totpVerify("secret", "123456");
        verify(throttlePolicy).recordSecondFactorSuccess("alice", user);
        verify(redisService).deleteValue(StartLogin.preAuthKey("pre-auth"));
    }

    @Test
    void verifyShouldConsumeBackupCodeWhenTotpDoesNotMatch() {
        UserDTO dto = new UserDTO();
        dto.setPreAuthToken("pre-auth");
        dto.setTotpCode("ABCDEFGH");

        UserDataBase user = new UserDataBase();
        user.setBackupCodes(new ArrayList<>(List.of("hash-1")));

        when(redisService.getValue(StartLogin.preAuthKey("pre-auth"))).thenReturn("Alice");
        when(verifier.findByUsernameOnly("Alice")).thenReturn(user);
        doThrow(new AuthExceptions.incorrectTotp("bad totp"))
                .when(totpVerifier).totpVerify(null, "ABCDEFGH");
        when(hasher.verify(any(char[].class), eq("hash-1"))).thenReturn(true);

        UserDataBase result = verifySecondFactor.verify(dto);

        assertSame(user, result);
        assertTrue(user.getBackupCodes().isEmpty());
        verify(userService).createUserInDataBase(user);
        verify(throttlePolicy).recordSecondFactorSuccess("alice", user);
    }

    @Test
    void verifyShouldRecordFailureWhenTotpAndBackupCodeDoNotMatch() {
        UserDTO dto = new UserDTO();
        dto.setPreAuthToken("pre-auth");
        dto.setTotpCode("ABCDEFGH");

        UserDataBase user = new UserDataBase();
        user.setBackupCodes(new ArrayList<>(List.of("hash-1")));

        when(redisService.getValue(StartLogin.preAuthKey("pre-auth"))).thenReturn("Alice");
        when(verifier.findByUsernameOnly("Alice")).thenReturn(user);
        doThrow(new AuthExceptions.incorrectTotp("bad totp"))
                .when(totpVerifier).totpVerify(null, "ABCDEFGH");
        when(hasher.verify(any(char[].class), eq("hash-1"))).thenReturn(false);

        AuthExceptions.InvalidCredentials thrown = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> verifySecondFactor.verify(dto));

        assertEquals("Invalid TOTP or Backup code.", thrown.getMessage());
        verify(throttlePolicy).recordSecondFactorFailure("alice", user);
        verify(redisService, never()).deleteValue(StartLogin.preAuthKey("pre-auth"));
    }
}
