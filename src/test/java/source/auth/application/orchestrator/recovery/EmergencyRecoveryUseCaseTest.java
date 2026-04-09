package source.auth.application.orchestrator.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.infra.persistance.redis.contracts.RedisContract;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.pow.PowService;
import source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.EmergencyRecoveryFinishRequest;
import source.auth.dto.EmergencyRecoveryFinishResponse;
import source.auth.dto.EmergencyRecoveryStartRequest;
import source.auth.dto.EmergencyRecoveryStartResponse;
import source.auth.dto.EmergencyRecoveryState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.notification.service.NotificationService;
import source.security.VaultKeyProvider;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmergencyRecoveryUseCaseTest {

    private SignupVerifier signupVerifier;
    private PowService powService;
    private Hasher hasher;
    private TOTPKeyGenerate totpKeyGenerate;
    private TOTPVerifier totpVerifier;
    private PasskeyService passkeyService;
    private UserRepository userRepository;
    private PasskeyCredentialRepository passkeyCredentialRepository;
    private RedisContract redisContract;
    private Cryptography cryptography;
    private VaultKeyProvider vaultKeyProvider;
    private NotificationService notificationService;

    private EmergencyRecoveryUseCase useCase;

    @BeforeEach
    void setUp() throws Exception {
        signupVerifier = mock(SignupVerifier.class);
        powService = mock(PowService.class);
        hasher = mock(Hasher.class);
        totpKeyGenerate = mock(TOTPKeyGenerate.class);
        totpVerifier = mock(TOTPVerifier.class);
        passkeyService = mock(PasskeyService.class);
        userRepository = mock(UserRepository.class);
        passkeyCredentialRepository = mock(PasskeyCredentialRepository.class);
        redisContract = mock(RedisContract.class);
        cryptography = mock(Cryptography.class);
        vaultKeyProvider = mock(VaultKeyProvider.class);
        notificationService = mock(NotificationService.class);

        AtomicInteger hashCounter = new AtomicInteger();
        when(hasher.hash(any(char[].class))).thenAnswer(invocation -> "hash-" + hashCounter.getAndIncrement());
        when(vaultKeyProvider.getMasterKey()).thenReturn(new SecretKeySpec(new byte[32], "AES"));

        useCase = new EmergencyRecoveryUseCase(
                signupVerifier,
                powService,
                hasher,
                totpKeyGenerate,
                totpVerifier,
                passkeyService,
                userRepository,
                passkeyCredentialRepository,
                redisContract,
                cryptography,
                vaultKeyProvider,
                notificationService);

        ReflectionTestUtils.setField(useCase, "requiredRecoveryCodes", 3);
        ReflectionTestUtils.setField(useCase, "recoverySessionTtlMinutes", 10L);
        ReflectionTestUtils.setField(useCase, "clientWindowSeconds", 600L);
        ReflectionTestUtils.setField(useCase, "clientMaxAttempts", 6L);
        ReflectionTestUtils.setField(useCase, "usernameWindowSeconds", 1800L);
        ReflectionTestUtils.setField(useCase, "usernameMaxAttempts", 4L);
        ReflectionTestUtils.setField(useCase, "recoveryBlockSeconds", 1800L);
    }

    @Test
    void startShouldCreateRecoverySessionWhenCodesMatch() throws Exception {
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

        when(powService.verifyChallenge("pow-challenge", "nonce")).thenReturn(true);
        when(redisContract.getValue(anyString())).thenReturn(null);
        when(redisContract.increment(anyString())).thenReturn(1L);
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(hasher.verify(any(char[].class), anyString())).thenAnswer(invocation -> {
            String code = new String(invocation.getArgument(0, char[].class));
            String storedHash = invocation.getArgument(1, String.class);
            return switch (storedHash) {
                case "current-passphrase-hash" -> false;
                case "stored-1" -> "12345678".equals(code);
                case "stored-2" -> "23456789".equals(code);
                case "stored-3" -> "34567890".equals(code);
                default -> false;
            };
        });
        when(totpKeyGenerate.keyGenerator()).thenReturn("BASE32SECRET");
        when(cryptography.encrypt(any(byte[].class), any())).thenReturn("ciphertext".getBytes(StandardCharsets.UTF_8));

        EmergencyRecoveryStartResponse response = useCase.start(request, "client-fingerprint");

        assertNotNull(response.getRecoverySessionId());
        assertTrue(response.getOtpUri().contains("BASE32SECRET"));
        assertNotNull(response.getPasskeyChallenge());
        assertEquals(600L, response.getExpiresInSeconds());
        assertEquals(3, response.getRequiredRecoveryCodes());
        verify(redisContract).saveEmergencyRecoveryState(anyString(), any(EmergencyRecoveryState.class), eq(10L));
    }

    @Test
    void finishShouldRotateCredentialsAndReplacePasskeys() throws Exception {
        EmergencyRecoveryState state = new EmergencyRecoveryState();
        state.setSessionId("session-1");
        state.setUsername("alice");
        state.setHashedPassphrase("new-passphrase-hash");
        state.setEncryptedTotpSecret(Base64.getEncoder().encodeToString("encrypted".getBytes(StandardCharsets.UTF_8)));
        state.setPasskeyChallenge("challenge-hex");
        state.setMatchedBackupCodeHashes(List.of("stored-1", "stored-2", "stored-3"));

        UserDataBase user = new UserDataBase();
        user.setUsername("alice");
        user.setPassphrase("old-hash");
        user.setBackupCodes(new ArrayList<>(List.of("stored-1", "stored-2", "stored-3", "stored-4")));
        ReflectionTestUtils.setField(user, "id", 7L);

        EmergencyRecoveryFinishRequest request = new EmergencyRecoveryFinishRequest();
        request.setRecoverySessionId("session-1");
        request.setTotpCode("123456");
        request.setPublicKeyCose(Base64.getEncoder().encodeToString("public-key".getBytes(StandardCharsets.UTF_8)));
        request.setCredentialId(Base64.getEncoder().encodeToString("credential-id".getBytes(StandardCharsets.UTF_8)));
        request.setUserHandle(Base64.getEncoder().encodeToString("user-handle".getBytes(StandardCharsets.UTF_8)));
        request.setSignature("signature");
        request.setAuthData("authData");
        request.setClientDataJSON("clientData");
        request.setDeviceName("Recovery Device");

        when(redisContract.getdelEmergencyRecoveryState("session-1")).thenReturn(state);
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(cryptography.decrypt(any(byte[].class), any())).thenReturn("BASE32SECRET".getBytes(StandardCharsets.UTF_8));
        when(totpVerifier.totpMatcher("BASE32SECRET", "123456")).thenReturn(true);
        when(passkeyService.verifySignature(eq("alice"), eq("challenge-hex"), eq("signature"), any(byte[].class),
                eq("authData"), eq("clientData"))).thenReturn(true);
        when(passkeyCredentialRepository.findByUserId(7L)).thenReturn(List.of(new PasskeyCredential()));
        when(userRepository.save(any(UserDataBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(notificationService).notifyUser(anyLong(), anyString(), anyString());

        EmergencyRecoveryFinishResponse response = useCase.finish(request);

        assertEquals("alice", response.getUsername());
        assertEquals(10, response.getNewBackupCodes().size());
        assertEquals("new-passphrase-hash", user.getPassphrase());
        assertEquals("BASE32SECRET", user.getTOTPSecret());
        assertEquals(10, user.getBackupCodes().size());
        assertFalse(user.getBackupCodes().contains("stored-1"));
        verify(passkeyCredentialRepository).deleteAll(any());
        verify(passkeyCredentialRepository).save(any(PasskeyCredential.class));
        verify(notificationService).notifyUser(eq(7L), anyString(), anyString());
    }
}
