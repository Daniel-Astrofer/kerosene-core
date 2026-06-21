package source.auth.application.usecase.passkey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatePasskeyDeviceStatusUseCaseTest {

    private UserRepository userRepository;
    private PasskeyCredentialRepository passkeyCredentialRepository;
    private PasskeyInventoryService passkeyInventoryService;
    private UpdatePasskeyDeviceStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passkeyCredentialRepository = mock(PasskeyCredentialRepository.class);
        passkeyInventoryService = mock(PasskeyInventoryService.class);
        useCase = new UpdatePasskeyDeviceStatusUseCase(
                userRepository,
                passkeyCredentialRepository,
                passkeyInventoryService);
    }

    @Test
    void executeReturnsUserNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        UpdatePasskeyDeviceStatusUseCase.Result result = useCase.execute(42L, "device-1", "BLOCKED");

        assertEquals(UpdatePasskeyDeviceStatusUseCase.Status.USER_NOT_FOUND, result.status());
        assertEquals("User not found", result.message());
        assertNull(result.inventory());
        verify(passkeyCredentialRepository, never()).findFirstByUserIdAndDeviceInstallId(any(), any());
        verify(passkeyCredentialRepository, never()).save(any());
    }

    @Test
    void executeReturnsDeviceNotFoundWhenCredentialDoesNotExist() {
        UserDataBase user = user(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passkeyCredentialRepository.findFirstByUserIdAndDeviceInstallId(42L, "device-1"))
                .thenReturn(Optional.empty());

        UpdatePasskeyDeviceStatusUseCase.Result result = useCase.execute(42L, "device-1", "REVOKED");

        assertEquals(UpdatePasskeyDeviceStatusUseCase.Status.DEVICE_NOT_FOUND, result.status());
        assertEquals("Device not found", result.message());
        assertNull(result.inventory());
        verify(passkeyCredentialRepository, never()).save(any());
        verify(passkeyInventoryService, never()).inventoryFor(any());
    }

    @Test
    void executeUpdatesCredentialStatusAndReturnsInventory() {
        UserDataBase user = user(42L);
        PasskeyCredential credential = new PasskeyCredential();
        PasskeyInventoryDTO inventory = new PasskeyInventoryDTO(true, true, false, "localhost", "localhost", List.of());

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passkeyCredentialRepository.findFirstByUserIdAndDeviceInstallId(42L, "device-1"))
                .thenReturn(Optional.of(credential));
        when(passkeyInventoryService.inventoryFor(user)).thenReturn(inventory);

        UpdatePasskeyDeviceStatusUseCase.Result result = useCase.execute(42L, "device-1", "blocked");

        assertEquals(UpdatePasskeyDeviceStatusUseCase.Status.UPDATED, result.status());
        assertNull(result.message());
        assertEquals("BLOCKED", credential.getStatus());
        assertEquals(inventory, result.inventory());
        verify(passkeyCredentialRepository).save(credential);
        verify(passkeyInventoryService).inventoryFor(user);
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
}
