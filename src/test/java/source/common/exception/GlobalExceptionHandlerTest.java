package source.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.auth.AuthExceptions;
import source.common.dto.ApiResponse;
import source.common.observability.FinancialOperationsMetrics;
import source.ledger.exceptions.LedgerExceptions;
import source.payments.exception.PaymentException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private FinancialOperationsMetrics financialMetrics;

    @BeforeEach
    void setUp() {
        financialMetrics = mock(FinancialOperationsMetrics.class);
        handler = new GlobalExceptionHandler(financialMetrics);
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
        verify(financialMetrics).increment("validation_rejected", "rejected", "illegal_argument");
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

    @Test
    void shouldReturnConflictWhenReceiverIsNotReady() {
        LedgerExceptions.ReceiverNotReadyException ex = LedgerExceptions.ReceiverNotReadyException.inboundBlocked();

        ResponseEntity<ApiResponse<Object>> response = handler.handleReceiverNotReady(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ERR_LEDGER_RECEIVER_NOT_READY", response.getBody().getErrorCode());
        assertEquals("The destination user exists but is not yet ready to receive funds.",
                response.getBody().getMessage());
        assertEquals("INBOUND_BLOCKED", ((Map<?, ?>) response.getBody().getData()).get("reason"));
    }

    @Test
    void shouldPreservePaymentExceptionStatusAndCode() {
        PaymentException ex = PaymentException.conflict(
                "QUOTE_EXPIRED",
                "A cotação expirou. Gere uma nova antes de confirmar.");

        ResponseEntity<ApiResponse<Void>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("QUOTE_EXPIRED", response.getBody().getErrorCode());
        assertEquals("A cotação expirou. Gere uma nova antes de confirmar.", response.getBody().getMessage());
        verify(financialMetrics).increment("validation_rejected", "rejected", "payment_exception");
    }

    @Test
    void shouldSanitizeTechnicalPaymentExceptionMessage() {
        PaymentException ex = PaymentException.badRequest(
                "PAYMENT_INVALID",
                "SQLException: select * from payment_intents");

        ResponseEntity<ApiResponse<Void>> response = handler.handlePaymentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PAYMENT_INVALID", response.getBody().getErrorCode());
        assertEquals("The payment request could not be completed.", response.getBody().getMessage());
    }

    @Test
    void shouldPreserveKeroseneExceptionCode() {
        VaultException ex = new VaultException("Vault storage is temporarily unavailable.", ErrorCodes.VAULT_STORAGE_ERROR);

        ResponseEntity<ApiResponse<Void>> response = handler.handleKeroseneException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(ErrorCodes.VAULT_STORAGE_ERROR, response.getBody().getErrorCode());
        assertEquals("Vault storage is temporarily unavailable.", response.getBody().getMessage());
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
