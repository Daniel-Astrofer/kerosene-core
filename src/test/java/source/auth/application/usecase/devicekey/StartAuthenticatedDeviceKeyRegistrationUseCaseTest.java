package source.auth.application.usecase.devicekey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.model.entity.UserDataBase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartAuthenticatedDeviceKeyRegistrationUseCaseTest {

    private UserRepository userRepository;
    private DeviceKeyService deviceKeyService;
    private StartAuthenticatedDeviceKeyRegistrationUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        deviceKeyService = mock(DeviceKeyService.class);
        useCase = new StartAuthenticatedDeviceKeyRegistrationUseCase(userRepository, deviceKeyService);
    }

    @Test
    void returnsUserNotFoundWhenAuthenticatedUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        StartAuthenticatedDeviceKeyRegistrationUseCase.Result result = useCase.execute(42L);

        assertThat(result.status()).isEqualTo(StartAuthenticatedDeviceKeyRegistrationUseCase.Status.USER_NOT_FOUND);
        assertThat(result.challenge()).isNull();
        verify(userRepository).findById(42L);
        verify(deviceKeyService, never()).startAuthenticatedRegistrationChallenge(any());
    }

    @Test
    void startsAuthenticatedRegistrationChallengeForUser() {
        UserDataBase user = new UserDataBase();
        DeviceKeyChallengeResponse challenge = new DeviceKeyChallengeResponse(
                "challenge-id",
                "challenge",
                120L,
                "onion",
                "Ed25519",
                "v1");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(deviceKeyService.startAuthenticatedRegistrationChallenge(user)).thenReturn(challenge);

        StartAuthenticatedDeviceKeyRegistrationUseCase.Result result = useCase.execute(42L);

        assertThat(result.status()).isEqualTo(StartAuthenticatedDeviceKeyRegistrationUseCase.Status.GENERATED);
        assertThat(result.challenge()).isEqualTo(challenge);
    }
}
