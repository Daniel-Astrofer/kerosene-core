package source.auth.application.service.identityaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.PasskeyVerificationProjection;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalAuthenticationServiceTest {

    @Mock
    private PasskeyService passkeyService;

    @Mock
    private PasskeyInventoryService passkeyInventoryService;

    @Mock
    private PasskeyCredentialRepository passkeyCredentialRepository;

    @Mock
    private TOTPVerifier totpVerifier;

    @Mock
    private Hasher hasher;

    @Mock
    private UserServiceContract userService;

    @Mock
    private PlatformTransactionSignerPort platformTransactionSigner;

    private TransactionalAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new TransactionalAuthenticationService(
                passkeyService,
                passkeyInventoryService,
                passkeyCredentialRepository,
                totpVerifier,
                hasher,
                userService,
                platformTransactionSigner,
                new ObjectMapper());
    }

    @Test
    void standardWalletOutboundDoesNotAskTotpAndRequiresPasskeyChallenge() {
        UserDataBase user = user(AccountSecurityType.STANDARD);
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(passkeyService.generateChallenge("alice")).thenReturn("challenge");

        AuthExceptions.AuthValidationException ex = assertThrows(
                AuthExceptions.AuthValidationException.class,
                () -> service.authorize(TransactionalAuthenticationRequest.walletOutbound(
                        1L,
                        1L,
                        "BASE32SECRET",
                        null,
                        null,
                        null)));

        assertTrue(ex.getMessage().contains("PASSKEY_CHALLENGE_REQUIRED:challenge"));
        verifyNoInteractions(totpVerifier);
    }

    @Test
    void elevatedWalletOutboundStillRequiresTotpBeforePassphrase() {
        UserDataBase user = user(AccountSecurityType.MULTISIG_2FA);
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));

        assertThrows(
                AuthExceptions.IncorrectTotpException.class,
                () -> service.authorize(TransactionalAuthenticationRequest.walletOutbound(
                        1L,
                        1L,
                        "BASE32SECRET",
                        null,
                        null,
                        "passphrase")));

        verifyNoInteractions(hasher);
    }

    @Test
    void passkeyAssertionUsesCredentialRepositoryAndRedisChallenge() {
        UserDataBase user = user(AccountSecurityType.PASSKEY);
        byte[] publicKey = new byte[] { 1, 2, 3 };
        byte[] credentialIdBytes = new byte[] { 9, 8, 7 };
        String credentialId = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] { 9, 8, 7 });
        PasskeyVerificationProjection credential = new PasskeyVerificationProjection(
                credentialIdBytes,
                publicKey,
                0L,
                "ACTIVE",
                "localhost",
                "localhost",
                1L,
                "alice",
                true);
        String assertionJson = """
                {
                  "signature": "signature",
                  "authData": "auth-data",
                  "clientDataJSON": "client-data",
                  "credentialId": "%s"
                }
                """.formatted(credentialId);

        when(passkeyCredentialRepository.findVerificationByCredentialIdAndUserId(any(byte[].class), eq(1L)))
                .thenReturn(Optional.of(credential));
        when(passkeyService.consumeChallengeFromRedis("alice")).thenReturn("challenge");
        when(passkeyService.verifyAuthenticationAssertion(
                eq("alice"),
                eq("challenge"),
                eq("signature"),
                eq(publicKey),
                eq("auth-data"),
                eq("client-data")))
                .thenReturn(new PasskeyService.PasskeyVerificationResult(true, 1L));
        when(passkeyCredentialRepository.advanceSignatureCount(eq(credentialIdBytes), eq(1L), eq(1L)))
                .thenReturn(1);

        assertDoesNotThrow(() -> service.authorize(TransactionalAuthenticationRequest.kfeTransaction(
                user,
                null,
                assertionJson,
                null)));

        verify(passkeyService).consumeChallengeFromRedis("alice");
        verify(passkeyCredentialRepository).advanceSignatureCount(eq(credentialIdBytes), eq(1L), eq(1L));
    }

    private UserDataBase user(AccountSecurityType securityType) {
        UserDataBase user = org.mockito.Mockito.mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("alice");
        when(user.getAccountSecurity()).thenReturn(securityType);
        return user;
    }
}
