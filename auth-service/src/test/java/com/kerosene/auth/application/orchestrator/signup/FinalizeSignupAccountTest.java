package com.kerosene.auth.application.orchestrator.signup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kerosene.common.financial.FinancialWalletProvisioningPort;
import com.kerosene.auth.application.orchestrator.signup.port.PasskeyGateway;
import com.kerosene.auth.application.orchestrator.signup.port.SignupStateStore;
import com.kerosene.auth.application.orchestrator.signup.port.UserNotifier;
import com.kerosene.auth.application.service.security.CosignerSecretService;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.dto.SignupState;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.auth.model.enums.AccountSecurityType;
import com.kerosene.security.VaultKeyProvider;
import com.kerosene.notification.model.UserNotificationPayload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private FinancialWalletProvisioningPort financialWalletProvisioningPort;

    private FinalizeSignupAccount service;

    @BeforeEach
    void setUp() {
        lenient().when(vaultKeyProvider.isReady()).thenReturn(true);
        service = new FinalizeSignupAccount(
                stateStore,
                userService,
                passkeyGateway,
                userNotifier,
                cosignerSecretService,
                vaultKeyProvider,
                financialWalletProvisioningPort);
    }

    @Test
    void executeShouldActivateAccountWithoutTotpSecretsWhenTotpWasSkipped() {
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

        UserDataBase created = service.execute("session-1");

        assertTrue(Boolean.TRUE.equals(created.getIsActive()));
        assertNotNull(created.getActivatedAt());
        assertNull(created.getTOTPSecret());
        assertEquals(List.of(), created.getBackupCodes());

        ArgumentCaptor<UserDataBase> userCaptor = ArgumentCaptor.forClass(UserDataBase.class);
        verify(userService, times(2)).createUserInDataBase(userCaptor.capture());
        UserDataBase persistedUser = userCaptor.getAllValues().get(0);
        UserDataBase activatedUser = userCaptor.getAllValues().get(1);
        assertEquals("alice", persistedUser.getUsername());
        assertTrue(Boolean.TRUE.equals(activatedUser.getIsActive()));
        assertNotNull(activatedUser.getActivatedAt());

        verify(stateStore).deleteSignupState("session-1");
        ArgumentCaptor<UserNotificationPayload> notificationCaptor =
                ArgumentCaptor.forClass(UserNotificationPayload.class);
        verify(userNotifier).notify(eq(7L), notificationCaptor.capture());
        assertEquals("Conta criada", notificationCaptor.getValue().title());
        assertEquals("Sua conta Kerosene foi criada com sucesso.", notificationCaptor.getValue().body());

        verify(financialWalletProvisioningPort).ensurePrimaryWalletReady(eq(7L), eq(null));
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

        UserDataBase created = service.execute("session-1");

        assertEquals("BASE32SECRET", created.getTOTPSecret());
        assertEquals(List.of("hash-a", "hash-b"), created.getBackupCodes());
        assertNotNull(created.getPasswordHash());
    }

    @Test
    void ensureUserFinancialsReadyRepairsPrimaryWalletOnLoginWithoutSignupState() {
        UserDataBase user = new UserDataBase();
        setUserId(user, 7L);

        service.ensureUserFinancialsReady(user, null);

        verify(financialWalletProvisioningPort).ensurePrimaryWalletReady(7L, null);
    }

    @Test
    void ensureUserFinancialsReadyUsesSignupAddressOnlyAfterCommit() {
        SignupState state = signupState(false);
        state.setBtcDepositAddress("bc1qsignup");
        UserDataBase user = new UserDataBase();
        setUserId(user, 7L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.ensureUserFinancialsReady(user, state);

            verify(financialWalletProvisioningPort, never()).ensurePrimaryWalletReady(any(), any());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(financialWalletProvisioningPort).ensurePrimaryWalletReady(7L, "bc1qsignup");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private SignupState signupState(boolean totpVerified) {
        SignupState state = new SignupState();
        state.setSessionId("session-1");
        state.setUsername("alice");
        state.setPassphrase("hashed-password");
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
