package source.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import source.auth.AuthExceptions;
import source.common.dto.ApiResponse;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.exceptions.WalletExceptions;

/**
 * Global centralized component to intercept all exceptions and return standard
 * ApiResponses
 * with specific HTTP statuses and heavy error messages for the frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // ============================================
        // AUTH EXCEPTIONS
        // ============================================

        @ExceptionHandler(AuthExceptions.UserAlreadyExistsException.class)
        public ResponseEntity<ApiResponse<Void>> handleUserAlreadyExists(AuthExceptions.UserAlreadyExistsException ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                "Failed to create account: A user with this username already exists. Please choose a different username.",
                                "ERR_AUTH_USER_ALREADY_EXISTS");
        }

        @ExceptionHandler(AuthExceptions.UsernameCantBeNull.class)
        public ResponseEntity<ApiResponse<Void>> handleUsernameNull(AuthExceptions.UsernameCantBeNull ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Validation Error: The username field cannot be null or empty. Please provide a valid username.",
                                "ERR_AUTH_USERNAME_MISSING");
        }

        @ExceptionHandler(AuthExceptions.PassphraseCantBeNull.class)
        public ResponseEntity<ApiResponse<Void>> handlePassphraseNull(AuthExceptions.PassphraseCantBeNull ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Validation Error: The passphrase field cannot be null or empty. Please provide a valid passphrase.",
                                "ERR_AUTH_PASSPHRASE_MISSING");
        }

        @ExceptionHandler(AuthExceptions.InvalidCharacterUsername.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidCharacter(AuthExceptions.InvalidCharacterUsername ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Validation Error: The username contains invalid characters. Please use only allowed alphanumeric characters.",
                                "ERR_AUTH_INVALID_USERNAME_FORMAT");
        }

        @ExceptionHandler(AuthExceptions.CharacterLimitException.class)
        public ResponseEntity<ApiResponse<Void>> handleCharLimit(AuthExceptions.CharacterLimitException ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Validation Error: The username or passphrase exceeds the allowed character limit. Please shorten your input.",
                                "ERR_AUTH_CHARACTER_LIMIT_EXCEEDED");
        }

        @ExceptionHandler(AuthExceptions.UserNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleUserNotFound(AuthExceptions.UserNotFoundException ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Authentication Failed: No account could be found matching the provided username. Verify the username and try again.",
                                "ERR_AUTH_USER_NOT_FOUND");
        }

        @ExceptionHandler(AuthExceptions.InvalidPassphrase.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidPassphrase(AuthExceptions.InvalidPassphrase ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Validation Error: The provided passphrase is invalid or does not meet the required BIP39 standards.",
                                "ERR_AUTH_INVALID_PASSPHRASE_FORMAT");
        }

        @ExceptionHandler(AuthExceptions.IncorrectTotpException.class)
        public ResponseEntity<ApiResponse<Void>> handleIncorrectTotp(AuthExceptions.IncorrectTotpException ex) {
                return buildErrorResponse(
                                HttpStatus.UNAUTHORIZED,
                                "Authentication Failed: The provided TOTP code is incorrect or expired. Please check your authenticator app and try again.",
                                "ERR_AUTH_INCORRECT_TOTP");
        }

        @ExceptionHandler(AuthExceptions.InvalidCredentials.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(AuthExceptions.InvalidCredentials ex) {
                return buildErrorResponse(
                                HttpStatus.UNAUTHORIZED,
                                "Authentication Failed: Invalid credentials provided. The username or passphrase you entered is incorrect.",
                                "ERR_AUTH_INVALID_CREDENTIALS");
        }

        @ExceptionHandler(AuthExceptions.UnrecognizedDeviceException.class)
        public ResponseEntity<ApiResponse<Void>> handleUnrecognizedDevice(
                        AuthExceptions.UnrecognizedDeviceException ex) {
                return buildErrorResponse(
                                HttpStatus.FORBIDDEN,
                                "Security Alert: Unrecognized device detected. For your protection, you must verify this new device using TOTP.",
                                "ERR_AUTH_UNRECOGNIZED_DEVICE");
        }

        @ExceptionHandler(AuthExceptions.TotpTimeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleTotpTimeout(AuthExceptions.TotpTimeExceededException ex) {
                return buildErrorResponse(
                                HttpStatus.REQUEST_TIMEOUT,
                                "Session Expired: The time limit for completing TOTP verification has been exceeded. Please restart the login or signup process.",
                                "ERR_AUTH_TOTP_TIMEOUT");
        }

        @ExceptionHandler(AuthExceptions.AuthValidationException.class)
        public ResponseEntity<ApiResponse<Void>> handleGenericAuthException(AuthExceptions.AuthValidationException ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Authentication Error: " + ex.getMessage(),
                                "ERR_AUTH_GENERIC");
        }

        // ============================================
        // LEDGER EXCEPTIONS
        // ============================================

        @ExceptionHandler(LedgerExceptions.LedgerNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleLedgerNotFound(LedgerExceptions.LedgerNotFoundException ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Ledger Error: The requested ledger could not be found. It may have been deleted or never created for this wallet.",
                                "ERR_LEDGER_NOT_FOUND");
        }

        @ExceptionHandler(LedgerExceptions.ReceiverNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleReceiverNotFound(LedgerExceptions.ReceiverNotFoundException ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Transaction Failed: The specified receiver (wallet, username, or address) does not exist in our system. Please verify the destination and try again.",
                                "ERR_LEDGER_RECEIVER_NOT_FOUND");
        }

        @ExceptionHandler(LedgerExceptions.LedgerAlreadyExistsException.class)
        public ResponseEntity<ApiResponse<Void>> handleLedgerAlreadyExists(
                        LedgerExceptions.LedgerAlreadyExistsException ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                "Ledger Error: A ledger already exists for this wallet. You cannot create a duplicate ledger.",
                                "ERR_LEDGER_ALREADY_EXISTS");
        }

        @ExceptionHandler(LedgerExceptions.InsufficientBalanceException.class)
        public ResponseEntity<ApiResponse<Void>> handleInsufficientBalance(
                        LedgerExceptions.InsufficientBalanceException ex) {
                return buildErrorResponse(
                                HttpStatus.PAYMENT_REQUIRED,
                                "Transaction Failed: Your ledger has insufficient balance to complete this transaction. Please add funds to your wallet.",
                                "ERR_LEDGER_INSUFFICIENT_BALANCE");
        }

        @ExceptionHandler(LedgerExceptions.InvalidLedgerOperationException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidLedgerOperation(
                        LedgerExceptions.InvalidLedgerOperationException ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Transaction Failed: You attempted an invalid ledger operation (e.g., negative amount). Review the transaction details.",
                                "ERR_LEDGER_INVALID_OPERATION");
        }

        @ExceptionHandler(LedgerExceptions.LedgerException.class)
        public ResponseEntity<ApiResponse<Void>> handleGenericLedgerException(LedgerExceptions.LedgerException ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Ledger Error: " + ex.getMessage(),
                                "ERR_LEDGER_GENERIC");
        }

        @ExceptionHandler(LedgerExceptions.PaymentRequestNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentRequestNotFound(
                        LedgerExceptions.PaymentRequestNotFoundException ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                ex.getMessage(),
                                "ERR_LEDGER_PAYMENT_REQUEST_NOT_FOUND");
        }

        @ExceptionHandler(LedgerExceptions.PaymentRequestExpiredException.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentRequestExpired(
                        LedgerExceptions.PaymentRequestExpiredException ex) {
                return buildErrorResponse(
                                HttpStatus.GONE,
                                ex.getMessage(),
                                "ERR_LEDGER_PAYMENT_REQUEST_EXPIRED");
        }

        @ExceptionHandler(LedgerExceptions.PaymentRequestAlreadyPaidException.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentRequestAlreadyPaid(
                        LedgerExceptions.PaymentRequestAlreadyPaidException ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_LEDGER_PAYMENT_REQUEST_ALREADY_PAID");
        }

        @ExceptionHandler(LedgerExceptions.PaymentRequestSelfPayException.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentRequestSelfPay(
                        LedgerExceptions.PaymentRequestSelfPayException ex) {
                return buildErrorResponse(
                                HttpStatus.FORBIDDEN,
                                ex.getMessage(),
                                "ERR_LEDGER_PAYMENT_REQUEST_SELF_PAY");
        }

        // ============================================
        // WALLET EXCEPTIONS
        // ============================================

        @ExceptionHandler(WalletExceptions.WalletNameAlredyExists.class)
        public ResponseEntity<ApiResponse<Void>> handleWalletNameExists(WalletExceptions.WalletNameAlredyExists ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                "Wallet Creation Failed: A wallet with this exact name already exists. Please choose a unique name for your new wallet.",
                                "ERR_WALLET_ALREADY_EXISTS");
        }

        @ExceptionHandler(WalletExceptions.WalletNoExists.class)
        public ResponseEntity<ApiResponse<Void>> handleWalletNotExists(WalletExceptions.WalletNoExists ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Wallet Not Found: The specified wallet does not exist. Please check the wallet name and try again.",
                                "ERR_WALLET_NOT_FOUND");
        }

        @ExceptionHandler(WalletExceptions.CreateWalletException.class)
        public ResponseEntity<ApiResponse<Void>> handleCreateWalletGeneric(WalletExceptions.CreateWalletException ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Wallet Error: An issue occurred while processing the wallet operation.",
                                "ERR_WALLET_GENERIC");
        }

        // ============================================
        // FALLBACK FOR UNEXPECTED ERRORS
        // ============================================

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
                if ("invalid user".equals(ex.getMessage())) {
                        return buildErrorResponse(
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication Failed: The user associated with this session no longer exists. Please log in again.",
                                        "ERR_AUTH_INVALID_USER");
                }
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Invalid request: " + ex.getMessage(),
                                "ERR_BAD_REQUEST");
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleAllOtherExceptions(Exception ex) {
                log.error("[GlobalExceptionHandler] Unhandled exception: {} — {}", ex.getClass().getName(),
                                ex.getMessage(), ex);
                return buildErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal Server Error: An unexpected error occurred on our end. Our team has been notified.",
                                "ERR_INTERNAL_SERVER");
        }

        private ResponseEntity<ApiResponse<Void>> buildErrorResponse(HttpStatus status, String message,
                        String errorCode) {
                return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode));
        }
}
