package source.auth.application.orchestrator.signup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.orchestrator.signup.port.OnboardingVoucherPort;
import source.auth.application.orchestrator.signup.port.PasskeyGateway;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.orchestrator.signup.port.UserNotifier;
import source.auth.application.service.security.CosignerSecretService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.SignupState;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.Voucher;
import source.auth.model.enums.AccountSecurityType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalizeSignupOnPaymentTest {

    @Mock
    private SignupStateStore stateStore;
    @Mock
    private UserServiceContract userService;
    @Mock
    private PasskeyGateway passkeyGateway;
    @Mock
    private OnboardingVoucherPort onboardingVoucherPort;
    @Mock
    private UserNotifier userNotifier;
    @Mock
    private CosignerSecretService cosignerSecretService;

    private FinalizeSignupOnPayment service;

    @BeforeEach
    void setUp() {
        service = new FinalizeSignupOnPayment(
                stateStore,
                userService,
                passkeyGateway,
                onboardingVoucherPort,
                userNotifier,
                cosignerSecretService);
    }

    @Test
    void shouldDeleteStateOnlyAfterSuccessfulFinalization() {
        SignupState state = signupState();
        UserDataBase persistedUser = new UserDataBase();
        setUserId(persistedUser, 7L);
        persistedUser.setUsername("alice");
        persistedUser.setAccountSecurity(AccountSecurityType.STANDARD);
        persistedUser.setIsActive(false);

        UserDataBase activeUser = new UserDataBase();
        setUserId(activeUser, 7L);
        activeUser.setUsername("alice");
        activeUser.setAccountSecurity(AccountSecurityType.STANDARD);
        activeUser.setIsActive(true);
        activeUser.setVoucher(new Voucher());

        when(stateStore.findSignupState("session-1")).thenReturn(state);
        when(userService.findByUsername("alice")).thenReturn(null);
        when(userService.createUserInDataBase(any(UserDataBase.class))).thenReturn(persistedUser);
        when(passkeyGateway.findByUserId(7L)).thenReturn(List.of());
        when(passkeyGateway.save(any(PasskeyCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.buscarPorId(7L)).thenReturn(Optional.of(activeUser));

        boolean finalized = service.execute("session-1", "tx-1", new BigDecimal("0.00022000"));

        assertTrue(finalized);
        verify(onboardingVoucherPort).createAndClaim(7L, "tx-1", new BigDecimal("0.00022000"));
        verify(stateStore).deleteSignupState("session-1");
        verify(userNotifier).notify(7L, "Account Created!",
                "Your onboarding payment reached 3 confirmations. Your account is now active.");

        ArgumentCaptor<UserDataBase> userCaptor = ArgumentCaptor.forClass(UserDataBase.class);
        verify(userService).createUserInDataBase(userCaptor.capture());
        assertEquals("alice", userCaptor.getValue().getUsername());
    }

    @Test
    void shouldPreserveSignupStateWhenVoucherClaimFails() {
        SignupState state = signupState();
        UserDataBase persistedUser = new UserDataBase();
        setUserId(persistedUser, 7L);
        persistedUser.setUsername("alice");
        persistedUser.setAccountSecurity(AccountSecurityType.STANDARD);
        persistedUser.setIsActive(false);

        when(stateStore.findSignupState("session-1")).thenReturn(state);
        when(userService.findByUsername("alice")).thenReturn(null);
        when(userService.createUserInDataBase(any(UserDataBase.class))).thenReturn(persistedUser);
        when(passkeyGateway.findByUserId(7L)).thenReturn(List.of());
        when(passkeyGateway.save(any(PasskeyCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("voucher failure"))
                .when(onboardingVoucherPort).createAndClaim(eq(7L), eq("tx-1"), eq(new BigDecimal("0.00022000")));

        assertThrows(IllegalStateException.class,
                () -> service.execute("session-1", "tx-1", new BigDecimal("0.00022000")));

        verify(stateStore, never()).deleteSignupState("session-1");
        verify(userNotifier, never()).notify(any(Long.class), any(String.class), any(String.class));
    }

    private SignupState signupState() {
        SignupState state = new SignupState();
        state.setSessionId("session-1");
        state.setUsername("alice");
        state.setPassphrase("hashed-passphrase".toCharArray());
        state.setTotpSecret("BASE32SECRET");
        state.setTotpVerified(true);
        state.setPasskeyRegistered(true);
        state.setPasskeyCredentialId("Y3JlZGVudGlhbA==");
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
