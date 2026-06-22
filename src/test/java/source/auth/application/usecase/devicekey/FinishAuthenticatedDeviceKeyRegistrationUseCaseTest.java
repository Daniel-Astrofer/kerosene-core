package source.auth.application.usecase.devicekey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishAuthenticatedDeviceKeyRegistrationUseCaseTest {

    private UserRepository userRepository;
    private DeviceKeyCredentialRepository deviceKeyRepository;
    private DeviceKeyService deviceKeyService;
    private FinishAuthenticatedDeviceKeyRegistrationUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        deviceKeyRepository = mock(DeviceKeyCredentialRepository.class);
        deviceKeyService = mock(DeviceKeyService.class);
        useCase = new FinishAuthenticatedDeviceKeyRegistrationUseCase(
                userRepository,
                deviceKeyRepository,
                deviceKeyService);
    }

    @Test
    void returnsUserNotFoundWhenAuthenticatedUserDoesNotExist() {
        DeviceKeyRegistrationRequest request = new DeviceKeyRegistrationRequest();
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        FinishAuthenticatedDeviceKeyRegistrationUseCase.Result result = useCase.execute(42L, request);

        assertThat(result.status()).isEqualTo(FinishAuthenticatedDeviceKeyRegistrationUseCase.Status.USER_NOT_FOUND);
        verify(userRepository).findById(42L);
        verify(deviceKeyService, never()).verifyRegistration(any(), any(), any());
        verify(deviceKeyRepository, never()).save(any());
    }

    @Test
    void verifiesRegistrationWithAuthenticatedUsernameAndPersistsCredential() {
        UserDataBase user = user(42L, "alice");
        DeviceKeyRegistrationRequest request = new DeviceKeyRegistrationRequest();
        DeviceKeyService.VerifiedDeviceKeyRegistration verified = verifiedRegistration();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(deviceKeyService.verifyRegistration(request, "", "alice")).thenReturn(verified);
        when(deviceKeyRepository.findByCredentialIdAndUserId("credential-1", 42L)).thenReturn(Optional.empty());

        FinishAuthenticatedDeviceKeyRegistrationUseCase.Result result = useCase.execute(42L, request);

        assertThat(result.status()).isEqualTo(FinishAuthenticatedDeviceKeyRegistrationUseCase.Status.REGISTERED);
        verify(deviceKeyService).verifyRegistration(request, "", "alice");

        ArgumentCaptor<DeviceKeyCredential> credentialCaptor = ArgumentCaptor.forClass(DeviceKeyCredential.class);
        verify(deviceKeyRepository).save(credentialCaptor.capture());
        DeviceKeyCredential credential = credentialCaptor.getValue();
        assertThat(credential.getUser()).isEqualTo(user);
        assertThat(credential.getCredentialId()).isEqualTo("credential-1");
        assertThat(credential.getUserHandle()).isEqualTo("user-handle");
        assertThat(credential.getPublicKeyEd25519()).isEqualTo("public-key");
        assertThat(credential.getAlgorithm()).isEqualTo(DeviceKeyService.ALGORITHM);
        assertThat(credential.getCounter()).isEqualTo(7L);
        assertThat(credential.getDeviceName()).isEqualTo("Work laptop");
        assertThat(credential.getDeviceInstallId()).isEqualTo("install-1");
        assertThat(credential.getKeyStorage()).isEqualTo("secure-enclave");
        assertThat(credential.getPlatform()).isEqualTo("linux");
        assertThat(credential.getBrowser()).isEqualTo("firefox");
        assertThat(credential.getBrand()).isEqualTo("brand");
        assertThat(credential.getModel()).isEqualTo("model");
        assertThat(credential.getSerialNumber()).isEqualTo("serial");
        assertThat(credential.getOnionServiceId()).isEqualTo("onion");
        assertThat(credential.getProtocolVersion()).isEqualTo(1);
        assertThat(credential.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void doesNotPersistWhenCredentialIsAlreadyLinkedToUser() {
        UserDataBase user = user(42L, "alice");
        DeviceKeyRegistrationRequest request = new DeviceKeyRegistrationRequest();
        DeviceKeyCredential existingCredential = new DeviceKeyCredential();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(deviceKeyService.verifyRegistration(request, "", "alice")).thenReturn(verifiedRegistration());
        when(deviceKeyRepository.findByCredentialIdAndUserId("credential-1", 42L))
                .thenReturn(Optional.of(existingCredential));

        FinishAuthenticatedDeviceKeyRegistrationUseCase.Result result = useCase.execute(42L, request);

        assertThat(result.status()).isEqualTo(FinishAuthenticatedDeviceKeyRegistrationUseCase.Status.REGISTERED);
        verify(deviceKeyRepository, never()).save(any());
    }

    private UserDataBase user(Long id, String username) {
        UserDataBase user = new UserDataBase();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        return user;
    }

    private DeviceKeyService.VerifiedDeviceKeyRegistration verifiedRegistration() {
        return new DeviceKeyService.VerifiedDeviceKeyRegistration(
                "credential-1",
                "user-handle",
                "public-key",
                "public-key-sha",
                7L,
                "Work laptop",
                "install-1",
                "secure-enclave",
                "linux",
                "firefox",
                "brand",
                "model",
                "serial",
                "onion");
    }
}
