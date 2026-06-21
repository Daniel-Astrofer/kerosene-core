package source.auth.application.service.identityaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalPasskeyIntegrationTest {

    @Test
    void kfeTransactionAcceptsRealEd25519PasskeyAssertionAndAdvancesCounter() throws Exception {
        Fixture fixture = fixture(2L);
        Services services = services(fixture, 0L);
        when(services.repository.advanceSignatureCount(eq(fixture.credentialId()), eq(1L), eq(2L)))
                .thenReturn(1);

        assertDoesNotThrow(() -> services.service.authorize(TransactionalAuthenticationRequest.kfeTransaction(
                services.user,
                null,
                fixture.assertionJson(),
                null)));

        verify(services.repository).advanceSignatureCount(fixture.credentialId(), 1L, 2L);
    }

    @Test
    void kfeTransactionRejectsReplayWhenSignatureCounterDoesNotIncrease() throws Exception {
        Fixture fixture = fixture(7L);
        Services services = services(fixture, 7L);

        AuthExceptions.StructuredAuthException exception = assertThrows(
                AuthExceptions.StructuredAuthException.class,
                () -> services.service.authorize(TransactionalAuthenticationRequest.kfeTransaction(
                        services.user,
                        null,
                        fixture.assertionJson(),
                        null)));

        assertEquals(ErrorCodes.AUTH_PASSKEY_REPLAY, exception.getErrorCode());
        verify(services.repository, never()).advanceSignatureCount(any(byte[].class), eq(1L), eq(7L));
    }

    private Services services(Fixture fixture, long storedSignatureCount) {
        RedisServicer redis = mock(RedisServicer.class);
        when(redis.getAndDeleteValue("passkey_challenge:alice")).thenReturn(fixture.challengeHex());

        PasskeyService passkeyService = new PasskeyService(
                redis,
                new ObjectMapper(),
                new ObjectMapper(),
                "http://localhost:8080",
                "localhost");

        PasskeyCredentialRepository repository = mock(PasskeyCredentialRepository.class);
        when(repository.findVerificationByCredentialIdAndUserId(any(byte[].class), eq(1L)))
                .thenReturn(Optional.of(new PasskeyVerificationProjection(
                        fixture.credentialId(),
                        fixture.rawPublicKey(),
                        storedSignatureCount,
                        "ACTIVE",
                        "localhost",
                        "localhost",
                        1L,
                        "alice",
                        true)));

        PasskeyInventoryService inventory = mock(PasskeyInventoryService.class);
        when(inventory.isKnownIncompatibleForCurrentLogin("localhost", "localhost")).thenReturn(false);

        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("alice");
        when(user.getAccountSecurity()).thenReturn(AccountSecurityType.PASSKEY);

        TransactionalAuthenticationService service = new TransactionalAuthenticationService(
                passkeyService,
                inventory,
                repository,
                mock(TOTPVerifier.class),
                mock(Hasher.class),
                mock(UserServiceContract.class),
                mock(PlatformTransactionSignerPort.class),
                new ObjectMapper());

        return new Services(service, repository, user);
    }

    private Fixture fixture(long signatureCount) throws Exception {
        String challengeHex = HexFormat.of().formatHex(new byte[] {
                1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1
        });
        byte[] challengeBytes = HexFormat.of().parseHex(challengeHex);
        String challengeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        byte[] clientData = ("""
                {"type":"webauthn.get","challenge":"%s","origin":"http://localhost:8080"}
                """.formatted(challengeB64Url)).getBytes(StandardCharsets.UTF_8);

        byte[] authData = new byte[37];
        byte[] rpIdHash = MessageDigest.getInstance("SHA-256").digest("localhost".getBytes(StandardCharsets.UTF_8));
        System.arraycopy(rpIdHash, 0, authData, 0, rpIdHash.length);
        authData[32] = 0x05;
        byte[] counter = ByteBuffer.allocate(Integer.BYTES).putInt(Math.toIntExact(signatureCount)).array();
        System.arraycopy(counter, 0, authData, 33, counter.length);

        byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientData);
        byte[] signedData = new byte[authData.length + clientDataHash.length];
        System.arraycopy(authData, 0, signedData, 0, authData.length);
        System.arraycopy(clientDataHash, 0, signedData, authData.length, clientDataHash.length);

        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(signedData);

        byte[] credentialId = new byte[] { 9, 8, 7, 6 };
        String assertionJson = """
                {
                  "signature": "%s",
                  "authData": "%s",
                  "clientDataJSON": "%s",
                  "credentialId": "%s"
                }
                """.formatted(
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(authData),
                Base64.getUrlEncoder().withoutPadding().encodeToString(clientData),
                Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId));

        return new Fixture(
                challengeHex,
                credentialId,
                Arrays.copyOfRange(keyPair.getPublic().getEncoded(), keyPair.getPublic().getEncoded().length - 32,
                        keyPair.getPublic().getEncoded().length),
                assertionJson);
    }

    private record Fixture(String challengeHex, byte[] credentialId, byte[] rawPublicKey, String assertionJson) {
    }

    private record Services(
            TransactionalAuthenticationService service,
            PasskeyCredentialRepository repository,
            UserDataBase user) {
    }
}
