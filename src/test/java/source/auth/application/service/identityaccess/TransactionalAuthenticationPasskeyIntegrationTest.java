package source.auth.application.service.identityaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.PasskeyVerificationProjection;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.common.exception.ErrorCodes;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalAuthenticationPasskeyIntegrationTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authorizesTransactionWithRealEd25519AssertionAndAdvancesCounterAtomically() throws Exception {
        Fixture fixture = buildServiceFixture(1);

        assertDoesNotThrow(() -> fixture.service.authorize(TransactionalAuthenticationRequest.kfeTransaction(
                fixture.user,
                null,
                fixture.assertionJson,
                null)));

        verify(fixture.repository).advanceSignatureCount(eq(fixture.credentialId), eq(1L), eq(2L));
    }

    @Test
    void rejectsTransactionWhenCounterWasAlreadyAdvancedByConcurrentReplay() throws Exception {
        Fixture fixture = buildServiceFixture(0);

        AuthExceptions.StructuredAuthException exception = assertThrows(
                AuthExceptions.StructuredAuthException.class,
                () -> fixture.service.authorize(TransactionalAuthenticationRequest.kfeTransaction(
                        fixture.user,
                        null,
                        fixture.assertionJson,
                        null)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCodes.AUTH_PASSKEY_REPLAY, exception.getErrorCode());
    }

    private Fixture buildServiceFixture(int counterAdvanceRows) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        request.addHeader("Host", "localhost");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        InMemoryRedisServicer redis = new InMemoryRedisServicer();
        PasskeyService passkeyService = new PasskeyService(
                redis,
                new ObjectMapper(),
                new ObjectMapper(),
                "http://localhost:3000",
                "localhost");

        String challenge = passkeyService.generateChallenge("alice");
        Assertion assertion = createAssertion("localhost", "http://localhost:3000", challenge, 2L);
        String assertionJson = """
                {
                  "signature": "%s",
                  "authData": "%s",
                  "clientDataJSON": "%s",
                  "credentialId": "%s"
                }
                """.formatted(
                        assertion.signature(),
                        assertion.authData(),
                        assertion.clientDataJson(),
                        Base64.getEncoder().encodeToString(assertion.credentialId()));

        PasskeyCredentialRepository repository = mock(PasskeyCredentialRepository.class);
        PasskeyInventoryService inventoryService = mock(PasskeyInventoryService.class);
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("alice");
        when(user.getAccountSecurity()).thenReturn(AccountSecurityType.PASSKEY);

        PasskeyVerificationProjection credential = new PasskeyVerificationProjection(
                assertion.credentialId(),
                assertion.rawPublicKey(),
                1L,
                "ACTIVE",
                "localhost",
                "localhost",
                1L,
                "alice",
                true);
        when(repository.findVerificationByCredentialIdAndUserId(any(byte[].class), eq(1L)))
                .thenReturn(Optional.of(credential));
        when(repository.advanceSignatureCount(eq(assertion.credentialId()), eq(1L), eq(2L)))
                .thenReturn(counterAdvanceRows);
        when(inventoryService.isKnownIncompatibleForCurrentLogin("localhost", "localhost")).thenReturn(false);

        TransactionalAuthenticationService service = new TransactionalAuthenticationService(
                passkeyService,
                inventoryService,
                repository,
                mock(TOTPVerifier.class),
                mock(Hasher.class),
                mock(UserServiceContract.class),
                mock(PlatformTransactionSignerPort.class),
                new ObjectMapper());

        return new Fixture(service, repository, user, assertion.credentialId(), assertionJson);
    }

    private Assertion createAssertion(String rpId, String origin, String challengeHex, long counter) throws Exception {
        byte[] challengeBytes = java.util.HexFormat.of().parseHex(challengeHex);
        String challengeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        byte[] clientDataBytes = ("""
                {"type":"webauthn.get","challenge":"%s","origin":"%s","crossOrigin":false}
                """.formatted(challengeB64Url, origin)).getBytes(StandardCharsets.UTF_8);

        byte[] authData = new byte[37];
        byte[] rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
        System.arraycopy(rpIdHash, 0, authData, 0, rpIdHash.length);
        authData[32] = 0x05;
        authData[33] = (byte) ((counter >>> 24) & 0xff);
        authData[34] = (byte) ((counter >>> 16) & 0xff);
        authData[35] = (byte) ((counter >>> 8) & 0xff);
        authData[36] = (byte) (counter & 0xff);

        byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes);
        byte[] signedData = new byte[authData.length + clientDataHash.length];
        System.arraycopy(authData, 0, signedData, 0, authData.length);
        System.arraycopy(clientDataHash, 0, signedData, authData.length, clientDataHash.length);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = generator.generateKeyPair();
        Signature signature = Signature.getInstance("EdDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signedData);

        byte[] rawPublicKey = Arrays.copyOfRange(
                keyPair.getPublic().getEncoded(),
                keyPair.getPublic().getEncoded().length - 32,
                keyPair.getPublic().getEncoded().length);

        return new Assertion(
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(authData),
                Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataBytes),
                rawPublicKey,
                rawPublicKey);
    }

    private record Fixture(
            TransactionalAuthenticationService service,
            PasskeyCredentialRepository repository,
            UserDataBase user,
            byte[] credentialId,
            String assertionJson) {
    }

    private record Assertion(
            String signature,
            String authData,
            String clientDataJson,
            byte[] rawPublicKey,
            byte[] credentialId) {
    }

    private static class InMemoryRedisServicer implements RedisServicer {
        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public void createTempUser(source.auth.dto.UserDTO dto) {
        }

        @Override
        public source.auth.dto.UserDTO getFromRedis(source.auth.dto.UserDTO dto) {
            return null;
        }

        @Override
        public void deleteFromRedis(source.auth.dto.UserDTO dto) {
        }

        @Override
        public Long increment(String key) {
            return 1L;
        }

        @Override
        public void expire(String key, long timeoutSeconds) {
        }

        @Override
        public String getValue(String key) {
            return values.get(key);
        }

        @Override
        public String getAndDeleteValue(String key) {
            return values.remove(key);
        }

        @Override
        public void setValue(String key, String value, long timeoutSeconds) {
            values.put(key, value);
        }

        @Override
        public void deleteValue(String key) {
            values.remove(key);
        }
    }
}
