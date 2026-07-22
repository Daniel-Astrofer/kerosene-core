package source.auth.application.usecase.devicekey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.model.entity.UserDataBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetDeviceKeyAuthenticationChallengeUseCaseTest {

    private UserRepository userRepository;
    private DeviceKeyService deviceKeyService;
    private GetDeviceKeyAuthenticationChallengeUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        deviceKeyService = mock(DeviceKeyService.class);
        useCase = new GetDeviceKeyAuthenticationChallengeUseCase(userRepository, deviceKeyService);
    }

    @Test
    void returnsUserNotFoundWhenNormalizedUsernameDoesNotExist() {
        when(userRepository.findByUsername("alice")).thenReturn(null);

        GetDeviceKeyAuthenticationChallengeUseCase.Result result = useCase.execute("  Alice  ");

        assertThat(result.status()).isEqualTo(GetDeviceKeyAuthenticationChallengeUseCase.Status.USER_NOT_FOUND);
        assertThat(result.message()).isEqualTo("User not found");
        assertThat(result.challenge()).isNull();
        verify(userRepository).findByUsername("alice");
        verify(deviceKeyService, never()).startAuthenticationChallenge(any());
    }

    @Test
    void startsAuthenticationChallengeForNormalizedUsername() {
        UserDataBase user = new UserDataBase();
        DeviceKeyChallengeResponse challenge = new DeviceKeyChallengeResponse(
                "challenge-id",
                "challenge",
                120L,
                "onion",
                "Ed25519",
                "v1");
        when(userRepository.findByUsername("alice")).thenReturn(user);
        when(deviceKeyService.startAuthenticationChallenge(user)).thenReturn(challenge);

        GetDeviceKeyAuthenticationChallengeUseCase.Result result = useCase.execute("  Alice  ");

        assertThat(result.status()).isEqualTo(GetDeviceKeyAuthenticationChallengeUseCase.Status.GENERATED);
        assertThat(result.message()).isNull();
        assertThat(result.challenge()).isEqualTo(challenge);
    }
}
