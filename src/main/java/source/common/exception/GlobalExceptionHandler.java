package source.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import source.auth.AuthExceptions;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.common.dto.ApiResponse;
import source.common.infra.logging.StructuredLogField;
import source.common.observability.FinancialOperationsMetrics;
import source.common.exception.FinancialSelfPaymentException;
import source.common.exception.FinancialProviderUnavailableException;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_ARGUMENTS_MESSAGE = "The request contained invalid arguments.";
    private static final Pattern TECHNICAL_MESSAGE_PATTERN = Pattern.compile(
            "(?i)(exception|stacktrace|sql|jdbc|hibernate|database|nullpointer|select\\s|insert\\s|update\\s|delete\\s|org\\.|java\\.|source\\.|\\bat\\s+[a-z0-9_$.]+\\(|\\{.*\\}|\\[.*\\])");

    private FinancialOperationsMetrics financialMetrics;

    public GlobalExceptionHandler() {
    }

    public GlobalExceptionHandler(FinancialOperationsMetrics financialMetrics) {
        this.financialMetrics = financialMetrics;
    }

    @Autowired(required = false)
    public void setFinancialMetrics(FinancialOperationsMetrics financialMetrics) {
        this.financialMetrics = financialMetrics;
    }

    @ExceptionHandler(AuthExceptions.UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserAlreadyExists(AuthExceptions.UserAlreadyExistsException ex) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Failed to create account: A user with this username already exists. Please choose a different username.",
                ErrorCodes.AUTH_USER_ALREADY_EXISTS);
    }

    @ExceptionHandler(AuthExceptions.UsernameCantBeNull.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameNull(AuthExceptions.UsernameCantBeNull ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error: The username field cannot be null or empty. Please provide a valid username.",
                ErrorCodes.AUTH_USERNAME_NULL);
    }

    @ExceptionHandler(AuthExceptions.PassphraseCantBeNull.class)
    public ResponseEntity<ApiResponse<Void>> handlePassphraseNull(AuthExceptions.PassphraseCantBeNull ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error: The password field cannot be null or empty. Please provide a strong password.",
                ErrorCodes.AUTH_PASSPHRASE_NULL);
    }

    @ExceptionHandler(AuthExceptions.InvalidCharacterUsername.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCharacter(AuthExceptions.InvalidCharacterUsername ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error: The username contains invalid characters. Please use only allowed alphanumeric characters.",
                ErrorCodes.AUTH_INVALID_USERNAME_CHAR);
    }

    @ExceptionHandler(AuthExceptions.CharacterLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleCharLimit(AuthExceptions.CharacterLimitException ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error: The username or password exceeds the allowed character limit. Please shorten your input.",
                ErrorCodes.AUTH_CHARACTER_LIMIT);
    }

    @ExceptionHandler(AuthExceptions.UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(AuthExceptions.UserNotFoundException ex) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Authentication Failed: No account could be found matching the provided username. Verify the username and try again.",
                ErrorCodes.AUTH_USER_NOT_FOUND);
    }

    @ExceptionHandler(AuthExceptions.InvalidPassphrase.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPassphrase(AuthExceptions.InvalidPassphrase ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error: The provided password is invalid or does not meet the required strength policy.",
                ErrorCodes.AUTH_INVALID_PASSPHRASE);
    }

    @ExceptionHandler(AuthExceptions.IncorrectTotpException.class)
    public ResponseEntity<ApiResponse<Void>> handleIncorrectTotp(AuthExceptions.IncorrectTotpException ex) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed: The provided TOTP code is incorrect or expired. Please check your authenticator app and try again.",
                ErrorCodes.AUTH_INCORRECT_TOTP);
    }

    @ExceptionHandler(AuthExceptions.InvalidCredentials.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(AuthExceptions.InvalidCredentials ex) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed: Invalid credentials provided. The username or password you entered is incorrect.",
                ErrorCodes.AUTH_INVALID_CREDENTIALS);
    }

    @ExceptionHandler(AuthExceptions.UnrecognizedDeviceException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnrecognizedDevice(AuthExceptions.UnrecognizedDeviceException ex) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Security Alert: Unrecognized device detected. For your protection, you must verify this new device using TOTP.",
                ErrorCodes.AUTH_UNRECOGNIZED_DEVICE);
    }

    @ExceptionHandler(AuthExceptions.TotpTimeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTotpTimeout(AuthExceptions.TotpTimeExceededException ex) {
        return buildErrorResponse(
                HttpStatus.REQUEST_TIMEOUT,
                "Session Expired: The time limit for completing TOTP verification has been exceeded. Please restart the login or signup process.",
                ErrorCodes.AUTH_TOTP_TIMEOUT);
    }

    @ExceptionHandler(AuthExceptions.StructuredAuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleStructuredAuthException(AuthExceptions.StructuredAuthException ex) {
        return buildErrorResponse(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), ex.getData());
    }

    @ExceptionHandler(AuthExceptions.AuthValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericAuthException(AuthExceptions.AuthValidationException ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Authentication Error: " + ex.getMessage(),
                ErrorCodes.AUTH_GENERIC);
    }

    @ExceptionHandler(AuthExceptions.InboundReceivingBlockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleInboundReceivingBlocked(
            AuthExceptions.InboundReceivingBlockedException ex) {
        return buildErrorResponse(
                HttpStatus.PAYMENT_REQUIRED,
                ex.getMessage(),
                "ERR_ACCOUNT_DEPOSIT_REQUIRED",
                Map.of("guidance", "Ative uma carteira ou adicione saldo para receber pela plataforma."));
    }

    @ExceptionHandler(FinalizeSignupAccount.VaultNotReadyException.class)
    public ResponseEntity<ApiResponse<Object>> handleVaultNotReady(FinalizeSignupAccount.VaultNotReadyException ex) {
        log.warn("[GlobalExceptionHandler] Vault master key not ready during signup: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage(),
                "ERR_VAULT_NOT_READY",
                Map.of("guidance", "O cofre master não pôde ser ativado para esta conta. Tente novamente em instantes."));
    }

    @ExceptionHandler(FinancialProviderUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleKfeProviderUnavailable(FinancialProviderUnavailableException ex) {
        incrementFinancialMetric("provider_unavailable", "unavailable", "kfe_rail");
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                sanitizeMessage(ex.getMessage(), "KFE rail provider is unavailable."),
                "ERR_KFE_RAIL_PROVIDER_UNAVAILABLE",
                Map.of("guidance", "Os serviços de custódia e trilhos financeiros estão indisponíveis no momento. Tente novamente mais tarde."));
    }


    @ExceptionHandler(FinancialSelfPaymentException.class)
    public ResponseEntity<ApiResponse<Object>> handleKfeSelfPayment(FinancialSelfPaymentException ex) {
        incrementFinancialMetric("validation_rejected", "rejected", "self_payment");
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                ErrorCodes.LEDGER_PAYMENT_SELF_PAY,
                Map.of("guidance", "Use um link ou destino de outra conta para concluir esta transacao."));
    }


    @ExceptionHandler(StructuredPlatformException.class)
    public ResponseEntity<ApiResponse<Object>> handleStructuredPlatformException(StructuredPlatformException ex) {
        return buildErrorResponse(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), ex.getData());
    }

    @ExceptionHandler(KeroseneException.class)
    public ResponseEntity<ApiResponse<Void>> handleKeroseneException(KeroseneException ex) {
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                sanitizeMessage(ex.getMessage(), "The requested platform service is temporarily unavailable."),
                ex.getErrorCode());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        incrementFinancialMetric("validation_rejected", "rejected", "illegal_state");
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                sanitizeMessage(ex.getMessage(), "The requested operation cannot be completed in the current state."),
                "ERR_OPERATION_STATE");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        if ("invalid user".equals(ex.getMessage())) {
            return buildErrorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication Failed: The user associated with this session no longer exists. Please log in again.",
                    ErrorCodes.AUTH_INVALID_CREDENTIALS);
        }
        incrementFinancialMetric("validation_rejected", "rejected", "illegal_argument");
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                sanitizeMessage(ex.getMessage(), INVALID_ARGUMENTS_MESSAGE),
                ErrorCodes.SYS_INVALID_ARGUMENTS);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        incrementFinancialMetric("validation_rejected", "rejected", "bean_validation");
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                sanitizeMessage(message, INVALID_ARGUMENTS_MESSAGE),
                ErrorCodes.SYS_INVALID_ARGUMENTS);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        incrementFinancialMetric("validation_rejected", "rejected", "constraint_violation");
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                INVALID_ARGUMENTS_MESSAGE,
                ErrorCodes.SYS_INVALID_ARGUMENTS);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllOtherExceptions(Exception ex) {
        log.error("[GlobalExceptionHandler] Unhandled exception: {} - {}", ex.getClass().getName(), ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error: An unexpected error occurred on our end. Our team has been notified.",
                ErrorCodes.SYS_INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(HttpStatus status, String message, String errorCode) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode, null, currentTraceId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(
            HttpStatus status,
            String message,
            String errorCode,
            T data) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode, data, currentTraceId()));
    }

    private String currentTraceId() {
        String traceId = safeMdcValue(MDC.get(StructuredLogField.TRACE_ID));
        if (traceId != null) {
            return traceId;
        }
        return safeMdcValue(MDC.get(StructuredLogField.CORRELATION_ID));
    }

    private String safeMdcValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void incrementFinancialMetric(String name, String outcome, String type) {
        if (financialMetrics == null) {
            return;
        }
        try {
            financialMetrics.increment(name, outcome, type);
        } catch (RuntimeException exception) {
            log.warn("[GlobalExceptionHandler] Failed to increment financial metric {}: {}", name, exception.getMessage());
        }
    }

    private String sanitizeMessage(String rawMessage, String fallbackMessage) {
        if (rawMessage == null) {
            return fallbackMessage;
        }
        String normalized = rawMessage.trim();
        if (normalized.isEmpty() || normalized.length() > 180) {
            return fallbackMessage;
        }
        if (TECHNICAL_MESSAGE_PATTERN.matcher(normalized).find()) {
            return fallbackMessage;
        }
        return normalized;
    }
}
