package source.transactions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.transactions.infra.MpcSidecarClient;
import source.wallet.model.WalletEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAuthorizationServiceTest {

    @Mock
    private TOTPVerifier totpVerifier;

    @Mock
    private PasskeyService passkeyService;

    @Mock
    private PasskeyCredentialRepository passkeyCredentialRepository;

    @Mock
    private UserServiceContract userService;

    @Mock
    private Hasher hasher;

    @Mock
    private MpcSidecarClient mpcClient;

    private WalletAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new WalletAuthorizationService(
                totpVerifier,
                passkeyService,
                passkeyCredentialRepository,
                userService,
                hasher,
                mpcClient);
    }

    @Test
    void standardOutboundTransferDoesNotAskTotpAndRequiresPasskeyChallenge() {
        UserDataBase user = user(AccountSecurityType.STANDARD);
        WalletEntity wallet = wallet(user);
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));
        when(user.getUsername()).thenReturn("alice");
        when(passkeyService.generateChallenge("alice")).thenReturn("challenge");

        AuthExceptions.AuthValidationException ex = assertThrows(
                AuthExceptions.AuthValidationException.class,
                () -> service.authorizeOutboundTransfer(1L, wallet, null, null, null));

        assertTrue(ex.getMessage().contains("PASSKEY_CHALLENGE_REQUIRED:challenge"));
        verifyNoInteractions(totpVerifier);
    }

    @Test
    void elevatedOutboundTransferStillRequiresTotp() {
        UserDataBase user = user(AccountSecurityType.MULTISIG_2FA);
        WalletEntity wallet = wallet(user);
        when(userService.buscarPorId(1L)).thenReturn(Optional.of(user));

        assertThrows(
                AuthExceptions.IncorrectTotpException.class,
                () -> service.authorizeOutboundTransfer(1L, wallet, null, null, "passphrase"));
    }

    private UserDataBase user(AccountSecurityType securityType) {
        UserDataBase user = org.mockito.Mockito.mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getAccountSecurity()).thenReturn(securityType);
        return user;
    }

    private WalletEntity wallet(UserDataBase user) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(10L);
        wallet.setName("MAIN");
        wallet.setUser(user);
        wallet.setTotpSecret("BASE32SECRET");
        return wallet;
    }
}
