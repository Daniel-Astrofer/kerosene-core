package source.auth.application.service.recovery.start.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import source.auth.application.infra.persistence.redis.contracts.RedisContract;
import source.auth.application.port.out.AuthUserGateway;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.pow.PowService;
import source.auth.application.service.recovery.RecoveryCodeService;
import source.auth.application.service.recovery.RecoveryRateLimitService;
import source.auth.application.service.recovery.start.EmergencyRecoveryStartContext;
import source.auth.dto.EmergencyRecoveryStartRequest;
import source.auth.model.entity.UserDataBase;

class EmergencyRecoveryStartChainTest {

    private SignupVerifier signupVerifier;
    private PowService powService;
    private Hasher hasher;
    private AuthUserGateway userGateway;
    private RedisContract redisContract;
    private RecoveryCodeService recoveryCodeService;
    private RecoveryRateLimitService recoveryRateLimitService;
    private EmergencyRecoveryStartChain chain;

    @BeforeEach
    void setUp() {
        signupVerifier = mock(SignupVerifier.class);
        powService = mock(PowService.class);
        hasher = mock(Hasher.class);
        userGateway = mock(AuthUserGateway.class);
        redisContract = mock(RedisContract.class);

        when(hasher.hash(any(char[].class))).thenReturn("dummy-hash");

        recoveryCodeService = new RecoveryCodeService(hasher);
        recoveryRateLimitService = new RecoveryRateLimitService(redisContract);
        ReflectionTestUtils.setField(recoveryRateLimitService, "clientWindowSeconds", 600L);
        ReflectionTestUtils.setField(recoveryRateLimitService, "clientMaxAttempts", 6L);
        ReflectionTestUtils.setField(recoveryRateLimitService, "usernameWindowSeconds", 1800L);
        ReflectionTestUtils.setField(recoveryRateLimitService, "usernameMaxAttempts", 4L);
        ReflectionTestUtils.setField(recoveryRateLimitService, "recoveryBlockSeconds", 1800L);

        EmergencyRecoveryStartRequestValidationHandler requestValidationHandler =
                new EmergencyRecoveryStartRequestValidationHandler(signupVerifier, recoveryCodeService);
        ReflectionTestUtils.setField(requestValidationHandler, "requiredRecoveryCodes", 3);

        chain = new EmergencyRecoveryStartChain(List.of(
                requestValidationHandler,
                new EmergencyRecoveryStartRateLimitHandler(recoveryRateLimitService),
                new EmergencyRecoveryStartProofOfWorkHandler(powService),
                new EmergencyRecoveryStartUserEligibilityHandler(
                        userGateway,
                        hasher,
                        recoveryCodeService,
                        recoveryRateLimitService),
                new EmergencyRecoveryStartCodeMatchHandler(recoveryCodeService, recoveryRateLimitService)));
    }

    @Test
    void handleShouldPropagateContextAcrossAllHandlers() {
        EmergencyRecoveryStartRequest request = new EmergencyRecoveryStartRequest();
        request.setUsername("Alice");
        request.setNewPassphrase("legal winner thank year wave sausage worth useful legal winner thank yellow".toCharArray());
        request.setRecoveryCodes(List.of("12345678", "23456789", "34567890"));
        request.setChallenge("pow-challenge");
        request.setNonce("nonce");

        UserDataBase user = new UserDataBase();
        user.setUsername("alice");
        user.setPassphrase("current-passphrase-hash");
        user.setBackupCodes(List.of("stored-1", "stored-2", "stored-3", "stored-4"));

        when(redisContract.getValue(anyString())).thenReturn(null);
        when(redisContract.increment(anyString())).thenReturn(1L);
        when(powService.verifyChallenge("pow-challenge", "nonce")).thenReturn(true);
        when(userGateway.findByUsername("alice")).thenReturn(user);
        when(hasher.verify(any(char[].class), anyString())).thenAnswer(invocation -> {
            String candidate = new String(invocation.getArgument(0, char[].class));
            String storedHash = invocation.getArgument(1, String.class);
            return switch (storedHash) {
                case "current-passphrase-hash" -> false;
                case "stored-1" -> "12345678".equals(candidate);
                case "stored-2" -> "23456789".equals(candidate);
                case "stored-3" -> "34567890".equals(candidate);
                default -> false;
            };
        });

        EmergencyRecoveryStartContext context = chain.handle(request, "client-fingerprint");

        assertEquals("alice", context.normalizedUsername());
        assertEquals(3, context.matchedRecoveryCodeHashes().size());
        assertTrue(context.matchedRecoveryCodeHashes().contains("stored-1"));
        assertSame(user, context.user());
    }
}
