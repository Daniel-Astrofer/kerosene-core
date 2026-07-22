package source.auth.application.usecase.me;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AppPinStatusDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.UserRole;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetCurrentUserProfileUseCaseTest {

    private UserServiceContract userServiceContract;
    private AppPinService appPinService;
    private GetCurrentUserProfileUseCase useCase;

    @BeforeEach
    void setUp() {
        userServiceContract = mock(UserServiceContract.class);
        appPinService = mock(AppPinService.class);
        useCase = new GetCurrentUserProfileUseCase(userServiceContract, appPinService);
    }

    @Test
    void executeReturnsNotFoundWhenUserDoesNotExist() {
        when(userServiceContract.buscarPorId(99L)).thenReturn(Optional.empty());

        GetCurrentUserProfileUseCase.Result result = useCase.execute(99L, "device-1");

        assertFalse(result.found());
        assertNull(result.profile());
        verify(userServiceContract).buscarPorId(99L);
        verifyNoInteractions(appPinService);
    }

    @Test
    void executeBuildsCurrentUserProfilePayload() {
        UserDataBase user = user(
                7L,
                "alice",
                UserRole.ADMIN,
                true,
                LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        when(userServiceContract.buscarPorId(7L)).thenReturn(Optional.of(user));
        when(appPinService.getStatus(user, "device-1")).thenReturn(appPin(true));

        GetCurrentUserProfileUseCase.Result result = useCase.execute(7L, "device-1");

        assertTrue(result.found());
        Map<String, Object> profile = result.profile();
        assertEquals("7", profile.get("id"));
        assertEquals("7", profile.get("userId"));
        assertEquals("alice", profile.get("username"));
        assertEquals("ADMIN", profile.get("role"));
        assertEquals(true, profile.get("isAdmin"));
        assertEquals(true, profile.get("passkeyEnabledForTransactions"));
        assertEquals(true, profile.get("appPinEnabled"));
        assertEquals("2026-01-02T03:04:05", profile.get("createdAt"));
        verify(appPinService).getStatus(user, "device-1");
    }

    @Test
    void executeKeepsFalseDefaultsAndOmitsMissingCreatedAt() {
        UserDataBase user = user(8L, "bob", UserRole.USER, null, null);

        when(userServiceContract.buscarPorId(8L)).thenReturn(Optional.of(user));
        when(appPinService.getStatus(user, null)).thenReturn(appPin(false));

        GetCurrentUserProfileUseCase.Result result = useCase.execute(8L, null);

        Map<String, Object> profile = result.profile();
        assertEquals("USER", profile.get("role"));
        assertEquals(false, profile.get("isAdmin"));
        assertEquals(false, profile.get("passkeyEnabledForTransactions"));
        assertEquals(false, profile.get("appPinEnabled"));
        assertFalse(profile.containsKey("createdAt"));
        verify(appPinService).getStatus(user, null);
    }

    private UserDataBase user(
            Long id,
            String username,
            UserRole role,
            Boolean passkeyEnabledForTransactions,
            LocalDateTime createdAt) {
        UserDataBase user = new UserDataBase();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setRole(role);
        user.setPasskeyEnabledForTransactions(passkeyEnabledForTransactions);
        user.setCreatedAt(createdAt);
        return user;
    }

    private AppPinStatusDTO appPin(boolean enabled) {
        return new AppPinStatusDTO(enabled, true, false, 0, 5, 5, 4, 12, true, true, null, null, null);
    }
}
