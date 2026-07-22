package source.auth.application.usecase.devicekey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.SignupState;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartOnboardingDeviceKeyRegistrationUseCaseTest {

    private SignupStateStore signupStateStore;
    private DeviceKeyService deviceKeyService;
    private StartOnboardingDeviceKeyRegistrationUseCase useCase;

    @BeforeEach
    void setUp() {
        signupStateStore = mock(SignupStateStore.class);
        deviceKeyService = mock(DeviceKeyService.class);
        useCase = new StartOnboardingDeviceKeyRegistrationUseCase(signupStateStore, deviceKeyService);
    }

    @Test
    void returnsSessionExpiredWhenSignupStateDoesNotExist() {
        when(signupStateStore.findSignupState("session-1")).thenReturn(null);

        StartOnboardingDeviceKeyRegistrationUseCase.Result result = useCase.execute("session-1", "alice");

        assertThat(result.status()).isEqualTo(StartOnboardingDeviceKeyRegistrationUseCase.Status.SESSION_EXPIRED);
        assertThat(result.challenge()).isNull();
        verify(deviceKeyService, never()).startRegistrationChallenge(any(), any());
    }

    @Test
    void normalizesAndSavesUsernameWhenStateHasNoUsername() {
        SignupState state = new SignupState();
        DeviceKeyChallengeResponse challenge = challenge();
        when(signupStateStore.findSignupState("session-1")).thenReturn(state);
        when(deviceKeyService.startRegistrationChallenge("session-1", "alice"))
                .thenReturn(challenge);

        StartOnboardingDeviceKeyRegistrationUseCase.Result result =
                useCase.execute("session-1", "  Alice  ");

        assertThat(result.status()).isEqualTo(StartOnboardingDeviceKeyRegistrationUseCase.Status.GENERATED);
        assertThat(result.challenge()).isEqualTo(challenge);
        assertThat(state.getUsername()).isEqualTo("alice");
        verify(signupStateStore).saveSignupState("session-1", state, Duration.ofMinutes(1440));
    }

    @Test
    void doesNotOverwriteExistingUsername() {
        SignupState state = new SignupState();
        state.setUsername("alice");
        DeviceKeyChallengeResponse challenge = challenge();
        when(signupStateStore.findSignupState("session-1")).thenReturn(state);
        when(deviceKeyService.startRegistrationChallenge("session-1", "alice"))
                .thenReturn(challenge);

        StartOnboardingDeviceKeyRegistrationUseCase.Result result =
                useCase.execute("session-1", "mallory");

        assertThat(result.status()).isEqualTo(StartOnboardingDeviceKeyRegistrationUseCase.Status.GENERATED);
        assertThat(result.challenge()).isEqualTo(challenge);
        verify(signupStateStore, never()).saveSignupState(eq("session-1"), any(SignupState.class), any());
    }

    @Test
    void doesNotSaveBlankUsername() {
        SignupState state = new SignupState();
        DeviceKeyChallengeResponse challenge = challenge();
        when(signupStateStore.findSignupState("session-1")).thenReturn(state);
        when(deviceKeyService.startRegistrationChallenge("session-1", null))
                .thenReturn(challenge);

        StartOnboardingDeviceKeyRegistrationUseCase.Result result =
                useCase.execute("session-1", "   ");

        assertThat(result.status()).isEqualTo(StartOnboardingDeviceKeyRegistrationUseCase.Status.GENERATED);
        assertThat(result.challenge()).isEqualTo(challenge);
        verify(signupStateStore, never()).saveSignupState(eq("session-1"), any(SignupState.class), any());
    }

    private DeviceKeyChallengeResponse challenge() {
        return new DeviceKeyChallengeResponse(
                "challenge-id",
                "challenge",
                120L,
                "onion",
                "Ed25519",
                "v1");
    }
}
