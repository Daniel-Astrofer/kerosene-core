package source.auth.application.usecase.devicekey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.dto.devicekey.DeviceKeyDeviceDTO;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageDeviceKeyDevicesUseCaseTest {

    private UserRepository userRepository;
    private DeviceKeyCredentialRepository deviceKeyRepository;
    private ManageDeviceKeyDevicesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        deviceKeyRepository = mock(DeviceKeyCredentialRepository.class);
        useCase = new ManageDeviceKeyDevicesUseCase(userRepository, deviceKeyRepository);
    }

    @Test
    void listDevicesReturnsUserNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        ManageDeviceKeyDevicesUseCase.Result result = useCase.listDevices(42L);

        assertThat(result.status()).isEqualTo(ManageDeviceKeyDevicesUseCase.Status.USER_NOT_FOUND);
        assertThat(result.devices()).isNull();
        verify(deviceKeyRepository, never()).findByUserId(any());
    }

    @Test
    void listDevicesConvertsCredentialsToDtos() {
        UserDataBase user = user(42L);
        DeviceKeyCredential credential = credential("credential-1", "Phone", "ACTIVE");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(deviceKeyRepository.findByUserId(42L)).thenReturn(List.of(credential));

        ManageDeviceKeyDevicesUseCase.Result result = useCase.listDevices(42L);

        assertThat(result.status()).isEqualTo(ManageDeviceKeyDevicesUseCase.Status.LISTED);
        assertThat(result.devices()).hasSize(1);
        DeviceKeyDeviceDTO dto = result.devices().get(0);
        assertThat(dto.credentialId()).isEqualTo("credential-1");
        assertThat(dto.deviceName()).isEqualTo("Phone");
        assertThat(dto.status()).isEqualTo("ACTIVE");
    }

    @Test
    void revokeDeviceReturnsUserNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        ManageDeviceKeyDevicesUseCase.Result result = useCase.revokeDevice(42L, "credential-1");

        assertThat(result.status()).isEqualTo(ManageDeviceKeyDevicesUseCase.Status.USER_NOT_FOUND);
        assertThat(result.devices()).isNull();
        verify(deviceKeyRepository, never()).findByCredentialIdAndUserId(any(), any());
        verify(deviceKeyRepository, never()).save(any());
    }

    @Test
    void revokeDeviceReturnsCredentialNotFoundWhenCredentialDoesNotExist() {
        UserDataBase user = user(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(deviceKeyRepository.findByCredentialIdAndUserId("credential-1", 42L))
                .thenReturn(Optional.empty());

        ManageDeviceKeyDevicesUseCase.Result result = useCase.revokeDevice(42L, "credential-1");

        assertThat(result.status()).isEqualTo(ManageDeviceKeyDevicesUseCase.Status.CREDENTIAL_NOT_FOUND);
        assertThat(result.devices()).isNull();
        verify(deviceKeyRepository, never()).save(any());
        verify(deviceKeyRepository, never()).findByUserId(any());
    }

    @Test
    void revokeDeviceMarksCredentialRevokedAndReturnsUpdatedDevices() {
        UserDataBase user = user(42L);
        DeviceKeyCredential credential = credential("credential-1", "Phone", "ACTIVE");
        DeviceKeyCredential listedCredential = credential("credential-1", "Phone", "REVOKED");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(deviceKeyRepository.findByCredentialIdAndUserId("credential-1", 42L))
                .thenReturn(Optional.of(credential));
        when(deviceKeyRepository.findByUserId(42L)).thenReturn(List.of(listedCredential));

        ManageDeviceKeyDevicesUseCase.Result result = useCase.revokeDevice(42L, "credential-1");

        assertThat(result.status()).isEqualTo(ManageDeviceKeyDevicesUseCase.Status.REVOKED);
        assertThat(credential.getStatus()).isEqualTo("REVOKED");
        assertThat(credential.getRevokedAt()).isNotNull();
        assertThat(result.devices()).hasSize(1);
        assertThat(result.devices().get(0).status()).isEqualTo("REVOKED");
        verify(deviceKeyRepository).save(credential);
    }

    private UserDataBase user(Long id) {
        UserDataBase user = new UserDataBase();
        try {
            java.lang.reflect.Field field = UserDataBase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
        return user;
    }

    private DeviceKeyCredential credential(String credentialId, String deviceName, String status) {
        DeviceKeyCredential credential = new DeviceKeyCredential();
        credential.setCredentialId(credentialId);
        credential.setDeviceName(deviceName);
        credential.setDeviceInstallId("install-1");
        credential.setKeyStorage("TEE");
        credential.setPlatform("Android");
        credential.setBrowser("Chrome");
        credential.setOnionServiceId("onion-1");
        credential.setStatus(status);
        credential.setProtocolVersion(1);
        return credential;
    }
}
