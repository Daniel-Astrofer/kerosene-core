package source.auth.application.orchestrator.signup;

import org.bitcoinj.crypto.MnemonicCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.orchestrator.signup.port.PasskeyGateway;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.orchestrator.signup.port.UserNotifier;
import source.auth.application.service.security.CosignerSecretService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.SignupState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.security.VaultKeyProvider;
import source.notification.model.UserNotificationPayload;
import source.wallet.application.port.in.CreateWalletUseCase;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.service.WalletContract;
import source.ledger.service.LedgerContract;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalizeSignupAccountTest {

    @Mock
    private SignupStateStore stateStore;
    @Mock
    private UserServiceContract userService;
    @Mock
    private PasskeyGateway passkeyGateway;
    @Mock
    private UserNotifier userNotifier;
    @Mock
    private CosignerSecretService cosignerSecretService;
    @Mock
    private VaultKeyProvider vaultKeyProvider;
    @Mock
    private CreateWalletUseCase walletUseCase;
    @Mock
    private WalletContract walletContract;
    @Mock
    private LedgerContract ledgerContract;

    private FinalizeSignupAccount service;

    @BeforeEach
    void setUp() {
        when(vaultKeyProvider.isReady()).thenReturn(true);
        service = new FinalizeSignupAccount(
                stateStore,
                userService,
                passkeyGateway,
                userNotifier,
                cosignerSecretService,
                vaultKeyProvider,
                walletUseCase,
                walletContract,
                ledgerContract);
    }

    @Test
    void executeShouldCreateInactiveAccountWithoutTotpSecretsWhenTotpWasSkipped() {
        SignupState state = signupState(false);
        when(stateStore.findSignupState("session-1")).thenReturn(state);
        when(userService.findByUsername("alice")).thenReturn(null);
        when(userService.createUserInDataBase(any(UserDataBase.class))).thenAnswer(invocation -> {
            UserDataBase user = invocation.getArgument(0);
            setUserId(user, 7L);
            return user;
        });
        when(passkeyGateway.findByUserId(7L)).thenReturn(List.of());
        when(passkeyGateway.save(any(PasskeyCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletContract.findByUserId(7L)).thenReturn(List.of());

        UserDataBase created = service.execute("session-1");

        assertFalse(Boolean.TRUE.equals(created.getIsActive()));
        assertNull(created.getActivatedAt());
        assertNull(created.getTOTPSecret());
        assertEquals(List.of(), created.getBackupCodes());

        ArgumentCaptor<UserDataBase> userCaptor = ArgumentCaptor.forClass(UserDataBase.class);
        verify(userService).createUserInDataBase(userCaptor.capture());
        assertEquals("alice", userCaptor.getValue().getUsername());
        assertFalse(Boolean.TRUE.equals(userCaptor.getValue().getIsActive()));
        assertNull(userCaptor.getValue().getActivatedAt());

        verify(stateStore).deleteSignupState("session-1");
        ArgumentCaptor<UserNotificationPayload> notificationCaptor =
                ArgumentCaptor.forClass(UserNotificationPayload.class);
        verify(userNotifier).notify(eq(7L), notificationCaptor.capture());
        assertEquals("Conta criada", notificationCaptor.getValue().title());
        assertEquals("Sua conta foi criada com sucesso.", notificationCaptor.getValue().body());

        ArgumentCaptor<WalletRequestDTO> walletRequestCaptor =
                ArgumentCaptor.forClass(WalletRequestDTO.class);
        verify(walletUseCase).createWallet(walletRequestCaptor.capture(), eq(7L));
        WalletRequestDTO walletRequest = walletRequestCaptor.getValue();
        assertEquals("ACCOUNT 01", walletRequest.name());
        assertEquals("KEROSENE", walletRequest.walletMode());
        assertNull(walletRequest.xpub());
        assertFalse("hashed-password".equals(walletRequest.passphrase()));
        assertValidBip39Mnemonic(walletRequest.passphrase());
    }

    @Test
    void executeShouldPersistTotpSecretAndBackupCodesWhenTotpWasVerified() {
        SignupState state = signupState(true);
        when(stateStore.findSignupState("session-1")).thenReturn(state);
        when(userService.findByUsername("alice")).thenReturn(null);
        when(userService.createUserInDataBase(any(UserDataBase.class))).thenAnswer(invocation -> {
            UserDataBase user = invocation.getArgument(0);
            setUserId(user, 7L);
            return user;
        });
        when(passkeyGateway.findByUserId(7L)).thenReturn(List.of());
        when(passkeyGateway.save(any(PasskeyCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletContract.findByUserId(7L)).thenReturn(List.of());

        UserDataBase created = service.execute("session-1");

        assertEquals("BASE32SECRET", created.getTOTPSecret());
        assertEquals(List.of("hash-a", "hash-b"), created.getBackupCodes());
        assertNotNull(created.getPasswordHash());
    }

    private SignupState signupState(boolean totpVerified) {
        SignupState state = new SignupState();
        state.setSessionId("session-1");
        state.setUsername("alice");
        state.setPassphrase("hashed-password".toCharArray());
        state.setTotpSecret("BASE32SECRET");
        state.setBackupCodes(List.of("hash-a", "hash-b"));
        state.setTotpVerified(totpVerified);
        state.setPasskeyRegistered(true);
        state.setAccountSecurity(AccountSecurityType.STANDARD);
        state.setPasskeyCredentialId("Y3JlZGVudGlhbC1pZA==");
        state.setPasskeyUserHandle("dXNlci1oYW5kbGU=");
        state.setPasskeyPublicKeyCose("cHVibGljLWtleQ==");
        state.setPasskeyDeviceName("Phone");
        return state;
    }

    private void assertValidBip39Mnemonic(String mnemonic) {
        assertNotNull(mnemonic);
        List<String> words = Arrays.asList(mnemonic.split(" "));
        assertEquals(12, words.size());
        try {
            MnemonicCode.INSTANCE.check(words);
        } catch (Exception exception) {
            throw new AssertionError("Expected onboarding wallet mnemonic to be valid BIP39.", exception);
        }
    }

    private void setUserId(UserDataBase user, Long id) {
        try {
            java.lang.reflect.Field field = UserDataBase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
