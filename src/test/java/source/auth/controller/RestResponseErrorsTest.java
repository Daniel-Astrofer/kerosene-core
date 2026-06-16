package source.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.auth.AuthExceptions;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RestResponseErrorsTest {

    private final RestResponseErrors handler = new RestResponseErrors();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void shouldReturnStandardApiResponseForDeprecatedAuthException() {
        ResponseEntity<ApiResponse<Void>> response = handler.invalidSession(
                new AuthExceptions.UnrrecognizedDevice("Device not recognized"),
                request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCodes.AUTH_UNRECOGNIZED_DEVICE, response.getBody().getErrorCode());
        assertEquals("Unrecognized device detected.", response.getBody().getMessage());
    }

    @Test
    void shouldReturnNotFoundForDeprecatedUserNoExists() {
        ResponseEntity<ApiResponse<Void>> response = handler.userNoExists(
                new AuthExceptions.UserNoExists("User not found"),
                request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCodes.AUTH_USER_NOT_FOUND, response.getBody().getErrorCode());
    }
}
