package source.auth.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.application.usecase.me.GetCurrentUserProfileUseCase;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MeControllerTest {

    private GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;
    private MeController controller;

    @BeforeEach
    void setUp() {
        getCurrentUserProfileUseCase = mock(GetCurrentUserProfileUseCase.class);
        controller = new MeController(getCurrentUserProfileUseCase);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserReturnsUnauthorizedEmptyBodyWhenUnauthenticated() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getCurrentUser("device-1");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verifyNoInteractions(getCurrentUserProfileUseCase);
    }

    @Test
    void getCurrentUserReturnsUnauthorizedWhenAuthNameIsInvalid() {
        SecurityContextHolder.getContext().setAuthentication(authenticatedToken("not-a-long"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getCurrentUser("device-1");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid token context", response.getBody().getMessage());
        assertEquals(ErrorCodes.AUTH_SESSION_EXPIRED, response.getBody().getErrorCode());
        verifyNoInteractions(getCurrentUserProfileUseCase);
    }

    @Test
    void getCurrentUserMapsMissingUserToNotFound() {
        SecurityContextHolder.getContext().setAuthentication(authenticatedToken("42"));
        when(getCurrentUserProfileUseCase.execute(42L, "device-1"))
                .thenReturn(new GetCurrentUserProfileUseCase.Result(false, null));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getCurrentUser("device-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found", response.getBody().getMessage());
        assertEquals(ErrorCodes.AUTH_USER_NOT_FOUND, response.getBody().getErrorCode());
        verify(getCurrentUserProfileUseCase).execute(42L, "device-1");
    }

    @Test
    void getCurrentUserMapsUseCaseProfileToSuccessResponse() {
        SecurityContextHolder.getContext().setAuthentication(authenticatedToken("42"));
        Map<String, Object> profile = Map.of("id", "42", "username", "alice");
        when(getCurrentUserProfileUseCase.execute(42L, "device-1"))
                .thenReturn(new GetCurrentUserProfileUseCase.Result(true, profile));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getCurrentUser("device-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User retrieved successfully", response.getBody().getMessage());
        assertSame(profile, response.getBody().getData());
        verify(getCurrentUserProfileUseCase).execute(42L, "device-1");
    }

    private TestingAuthenticationToken authenticatedToken(String name) {
        return new TestingAuthenticationToken(name, null, "ROLE_USER");
    }
}
