package source.auth.application.usecase.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AppPinStatusDTO;
import source.auth.dto.ConfigureAppPinRequestDTO;
import source.auth.dto.VerifyAppPinRequestDTO;
import source.auth.model.entity.UserDataBase;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppPinOperationsUseCaseTest {

    private UserServiceContract userService;
    private AppPinService appPinService;
    private AppPinOperationsUseCase useCase;

    @BeforeEach
    void setUp() {
        userService = mock(UserServiceContract.class);
        appPinService = mock(AppPinService.class);
        useCase = new AppPinOperationsUseCase(userService, appPinService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getStatusLoadsAuthenticatedUserAndDelegates() {
        UserDataBase user = user();
        AppPinStatusDTO status = appPin();
        authenticate("42");

        when(userService.buscarPorId(42L)).thenReturn(Optional.of(user));
        when(appPinService.getStatus(user, "device-1")).thenReturn(status);

        AppPinStatusDTO result = useCase.getStatus("device-1");

        assertSame(status, result);
        verify(userService).buscarPorId(42L);
        verify(appPinService).getStatus(user, "device-1");
    }

    @Test
    void configureLoadsAuthenticatedUserAndDelegates() {
        UserDataBase user = user();
        ConfigureAppPinRequestDTO request = new ConfigureAppPinRequestDTO();
        AppPinStatusDTO status = appPin();
        authenticate("7");

        when(userService.buscarPorId(7L)).thenReturn(Optional.of(user));
        when(appPinService.configure(user, "device-2", request)).thenReturn(status);

        AppPinStatusDTO result = useCase.configure("device-2", request);

        assertSame(status, result);
        verify(appPinService).configure(user, "device-2", request);
    }

    @Test
    void verifyLoadsAuthenticatedUserAndDelegatesPinOnly() {
        UserDataBase user = user();
        VerifyAppPinRequestDTO request = new VerifyAppPinRequestDTO();
        request.setPin("1234");
        AppPinStatusDTO status = appPin();
        authenticate("9");

        when(userService.buscarPorId(9L)).thenReturn(Optional.of(user));
        when(appPinService.verify(user, "device-3", "1234")).thenReturn(status);

        AppPinStatusDTO result = useCase.verify("device-3", request);

        assertSame(status, result);
        verify(appPinService).verify(user, "device-3", "1234");
    }

    @Test
    void rejectsMissingAuthenticationWithExistingMessage() {
        AuthExceptions.InvalidCredentials exception = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> useCase.getStatus("device-1"));

        assertEquals("Not authenticated.", exception.getMessage());
        verifyNoInteractions(userService, appPinService);
    }

    @Test
    void rejectsInvalidAuthenticationNameWithExistingMessage() {
        authenticate("not-a-number");

        AuthExceptions.InvalidCredentials exception = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> useCase.getStatus("device-1"));

        assertEquals("Invalid authentication context.", exception.getMessage());
        verifyNoInteractions(userService, appPinService);
    }

    @Test
    void rejectsUnknownAuthenticatedUserWithExistingMessage() {
        authenticate("42");

        when(userService.buscarPorId(42L)).thenReturn(Optional.empty());

        AuthExceptions.InvalidCredentials exception = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> useCase.getStatus("device-1"));

        assertEquals("Authenticated user not found.", exception.getMessage());
        verifyNoInteractions(appPinService);
    }

    private void authenticate(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(name, null, "ROLE_USER"));
    }

    private UserDataBase user() {
        return new UserDataBase();
    }

    private AppPinStatusDTO appPin() {
        return new AppPinStatusDTO(false, false, false, 0, 5, 5, 4, 12, true, true, null, null, null);
    }
}
