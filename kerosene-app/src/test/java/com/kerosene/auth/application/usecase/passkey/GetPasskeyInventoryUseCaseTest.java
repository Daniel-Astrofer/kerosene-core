package source.auth.application.usecase.passkey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.dto.PasskeyInventoryDTO;
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

class GetPasskeyInventoryUseCaseTest {

    private UserRepository userRepository;
    private PasskeyInventoryService passkeyInventoryService;
    private GetPasskeyInventoryUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passkeyInventoryService = mock(PasskeyInventoryService.class);
        useCase = new GetPasskeyInventoryUseCase(userRepository, passkeyInventoryService);
    }

    @Test
    void executeReturnsUserNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        GetPasskeyInventoryUseCase.Result result = useCase.execute(42L);

        assertEquals(GetPasskeyInventoryUseCase.Status.USER_NOT_FOUND, result.status());
        assertEquals("User not found", result.message());
        assertNull(result.inventory());
        verify(passkeyInventoryService, never()).inventoryFor(any());
    }

    @Test
    void executeReturnsInventoryForExistingUser() {
        UserDataBase user = user(42L);
        PasskeyInventoryDTO inventory = new PasskeyInventoryDTO(true, true, false, "localhost", "localhost", List.of());

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passkeyInventoryService.inventoryFor(user)).thenReturn(inventory);

        GetPasskeyInventoryUseCase.Result result = useCase.execute(42L);

        assertEquals(GetPasskeyInventoryUseCase.Status.FOUND, result.status());
        assertNull(result.message());
        assertEquals(inventory, result.inventory());
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
