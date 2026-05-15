package source.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.auth.AuthExceptions;
import source.common.dto.ApiResponse;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturnStandardizedErrorCode_WhenUserAlreadyExists() {
        AuthExceptions.UserAlreadyExistsException ex = new AuthExceptions.UserAlreadyExistsException("test_user");
        ResponseEntity<ApiResponse<Void>> response = handler.handleUserAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(ErrorCodes.AUTH_USER_ALREADY_EXISTS, response.getBody().getErrorCode());
        assertTrue(response.getBody().getMessage().contains("already exists"));
    }

    @Test
    void shouldSanitizeTechnicalMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Internal Error: NullPointerException at line 42 in DBDriver");
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodes.SYS_INVALID_ARGUMENTS, response.getBody().getErrorCode());
        assertEquals("The request contained invalid arguments.", response.getBody().getMessage());
    }

    @Test
    void shouldBlockStacktraceKeywords() {
        String riskyMessage = "SqlException: table 'users' does not exist in database";
        String sanitized = invokeSanitize(riskyMessage, "Fallback");
        assertEquals("Fallback", sanitized);
    }

    @Test
    void shouldBlockJsonLeakage() {
        String riskyMessage = "{\"key\":\"sensitive\"}";
        String sanitized = invokeSanitize(riskyMessage, "Fallback");
        assertEquals("Fallback", sanitized);
    }

    @Test
    void shouldAllowBusinessMessage() {
        String businessMessage = "Your wallet is empty. Please deposit.";
        String sanitized = invokeSanitize(businessMessage, "Fallback");
        assertEquals(businessMessage, sanitized);
    }

    private String invokeSanitize(String raw, String fallback) {
        try {
            java.lang.reflect.Method method = GlobalExceptionHandler.class.getDeclaredMethod("sanitizeMessage", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(handler, raw, fallback);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
