package source.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import source.auth.AuthExceptions;
import source.common.dto.ApiResponse;
import source.common.observability.FinancialOperationsMetrics;
import source.ledger.exceptions.LedgerExceptions;
import source.mining.exception.MiningExceptions;
import source.payments.exception.PaymentException;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.exception.PaymentLinkExceptions;
import source.transactions.exception.TransactionExceptions;
import source.wallet.exceptions.WalletExceptions;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Global centralized component to intercept all exceptions and return standard
 * ApiResponses
 * with specific HTTP statuses and heavy error messages for the frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
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

        // ============================================
        // AUTH EXCEPTIONS
        // ============================================

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
                                "ERR_AUTH_USERNAME_MISSING");
        }

        @ExceptionHandler(AuthExceptions.PassphraseCantBeNull.class)
        public ResponseEntity<ApiResponse<Void>> handlePassphraseNull(AuthExceptions.PassphraseCantBeNull ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Validation Error: The password field cannot be null or empty. Please provide a strong password.",
                                "ERR_AUTH_PASSWORD_MISSING");
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
                                "Validation Error: The username or password exceeds the allowed character limit. Please shorten your input.",
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
                                "Validation Error: The provided password is invalid or does not meet the required strength policy.",
                                "ERR_AUTH_INVALID_PASSWORD_FORMAT");
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
                                "Authentication Failed: Invalid credentials provided. The username or password you entered is incorrect.",
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

        @ExceptionHandler(AuthExceptions.StructuredAuthException.class)
        public ResponseEntity<ApiResponse<Object>> handleStructuredAuthException(AuthExceptions.StructuredAuthException ex) {
                return buildErrorResponse(
                                ex.getStatus(),
                                ex.getMessage(),
                                ex.getErrorCode(),
                                ex.getData());
        }

        @ExceptionHandler(AuthExceptions.AuthValidationException.class)
        public ResponseEntity<ApiResponse<Void>> handleGenericAuthException(AuthExceptions.AuthValidationException ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Authentication Error: " + ex.getMessage(),
                                "ERR_AUTH_GENERIC");
        }

        @ExceptionHandler(AuthExceptions.InboundReceivingBlockedException.class)
        public ResponseEntity<ApiResponse<Void>> handleInboundReceivingBlocked(
                        AuthExceptions.InboundReceivingBlockedException ex) {
                return buildErrorResponse(
                                HttpStatus.PAYMENT_REQUIRED,
                                ex.getMessage(),
                                "ERR_ACCOUNT_DEPOSIT_REQUIRED");
        }

        @ExceptionHandler(source.auth.application.orchestrator.signup.FinalizeSignupAccount.VaultNotReadyException.class)
        public ResponseEntity<ApiResponse<Void>> handleVaultNotReady(
                        source.auth.application.orchestrator.signup.FinalizeSignupAccount.VaultNotReadyException ex) {
                log.warn("[GlobalExceptionHandler] Vault master key not ready during signup: {}", ex.getMessage());
                return buildErrorResponse(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                ex.getMessage(),
                                "ERR_VAULT_NOT_READY");
        }

        // ============================================
        // LEDGER EXCEPTIONS
        // ============================================

        @ExceptionHandler(LedgerExceptions.LedgerNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleLedgerNotFound(LedgerExceptions.LedgerNotFoundException ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Ledger Error: " + ex.getMessage(),
                                "ERR_LEDGER_NOT_FOUND");
        }

        @ExceptionHandler(LedgerExceptions.ReceiverNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleReceiverNotFound(LedgerExceptions.ReceiverNotFoundException ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                "Transaction Failed: The specified receiver (wallet, username, or address) does not exist in our system. Please verify the destination and try again.",
                                "ERR_LEDGER_RECEIVER_NOT_FOUND");
        }

        @ExceptionHandler(LedgerExceptions.ReceiverNotReadyException.class)
        public ResponseEntity<ApiResponse<Object>> handleReceiverNotReady(
                        LedgerExceptions.ReceiverNotReadyException ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_LEDGER_RECEIVER_NOT_READY",
                                java.util.Map.of("reason", ex.getReason().name()));
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
                incrementFinancialMetric("validation_rejected", "rejected", "ledger_invalid_operation");
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                "Transaction Failed: You attempted an invalid ledger operation (e.g., negative amount). Review the transaction details.",
                                "ERR_LEDGER_INVALID_OPERATION");
        }

        @ExceptionHandler(LedgerExceptions.InvalidAmountException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidLedgerAmount(
                        LedgerExceptions.InvalidAmountException ex) {
                incrementFinancialMetric("validation_rejected", "rejected", "invalid_amount");
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                "ERR_LEDGER_INVALID_AMOUNT");
        }

        @ExceptionHandler(LedgerExceptions.DuplicateTransactionException.class)
        public ResponseEntity<ApiResponse<Void>> handleDuplicateLedgerTransaction(
                        LedgerExceptions.DuplicateTransactionException ex) {
                incrementFinancialMetric("idempotency_reused", "duplicate", "ledger");
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_LEDGER_DUPLICATE_TRANSACTION");
        }

        @ExceptionHandler(LedgerExceptions.TransactionReplayException.class)
        public ResponseEntity<ApiResponse<Void>> handleLedgerReplay(
                        LedgerExceptions.TransactionReplayException ex) {
                incrementFinancialMetric("idempotency_rejected", "rejected", "ledger");
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_LEDGER_REPLAY_DETECTED");
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

        @ExceptionHandler(WalletExceptions.WalletNameAlreadyExists.class)
        public ResponseEntity<ApiResponse<Void>> handleWalletNameExists(WalletExceptions.WalletNameAlreadyExists ex) {
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
        // EXTERNAL PAYMENTS
        // ============================================

        @ExceptionHandler(ExternalPaymentsExceptions.CustodyProviderUnavailable.class)
        public ResponseEntity<ApiResponse<Void>> handleCustodyProviderUnavailable(
                        ExternalPaymentsExceptions.CustodyProviderUnavailable ex) {
                return buildErrorResponse(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                ex.getMessage(),
                                "ERR_CUSTODY_PROVIDER_UNAVAILABLE");
        }

        @ExceptionHandler(ExternalPaymentsExceptions.InvalidNetworkAddress.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidNetworkAddress(
                        ExternalPaymentsExceptions.InvalidNetworkAddress ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                "ERR_INVALID_NETWORK_ADDRESS");
        }

        @ExceptionHandler(ExternalPaymentsExceptions.TransferNotFound.class)
        public ResponseEntity<ApiResponse<Void>> handleTransferNotFound(
                        ExternalPaymentsExceptions.TransferNotFound ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                ex.getMessage(),
                                "ERR_NETWORK_TRANSFER_NOT_FOUND");
        }

        @ExceptionHandler(ExternalPaymentsExceptions.TransferCancellationNotAllowed.class)
        public ResponseEntity<ApiResponse<Void>> handleTransferCancellationNotAllowed(
                        ExternalPaymentsExceptions.TransferCancellationNotAllowed ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_NETWORK_TRANSFER_CANCEL_NOT_ALLOWED");
        }

        @ExceptionHandler(ExternalPaymentsExceptions.DuplicateExternalPayment.class)
        public ResponseEntity<ApiResponse<Void>> handleDuplicateExternalPayment(
                        ExternalPaymentsExceptions.DuplicateExternalPayment ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_EXTERNAL_PAYMENT_DUPLICATE");
        }

        @ExceptionHandler(TransactionExceptions.TransactionBroadcastFailed.class)
        public ResponseEntity<ApiResponse<Void>> handleTransactionBroadcastFailed(
                        TransactionExceptions.TransactionBroadcastFailed ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_GATEWAY,
                                ex.getMessage(),
                                "ERR_TRANSACTION_BROADCAST_FAILED");
        }

        // ============================================
        // PAYMENT LINKS
        // ============================================

        @ExceptionHandler(PaymentLinkExceptions.PaymentLinkNotFound.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentLinkNotFound(
                        PaymentLinkExceptions.PaymentLinkNotFound ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                ex.getMessage(),
                                "ERR_PAYMENT_LINK_NOT_FOUND");
        }

        @ExceptionHandler(PaymentLinkExceptions.PaymentLinkExpired.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentLinkExpired(
                        PaymentLinkExceptions.PaymentLinkExpired ex) {
                return buildErrorResponse(
                                HttpStatus.GONE,
                                ex.getMessage(),
                                "ERR_PAYMENT_LINK_EXPIRED");
        }

        @ExceptionHandler(PaymentLinkExceptions.InvalidPaymentLinkState.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentLinkState(
                        PaymentLinkExceptions.InvalidPaymentLinkState ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_PAYMENT_LINK_INVALID_STATE");
        }

        @ExceptionHandler(PaymentLinkExceptions.InvalidPaymentLinkTransaction.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentLinkTransaction(
                        PaymentLinkExceptions.InvalidPaymentLinkTransaction ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                "ERR_PAYMENT_LINK_INVALID_TRANSACTION");
        }

        @ExceptionHandler(PaymentLinkExceptions.PaymentLinkCreditFailed.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentLinkCreditFailed(
                        PaymentLinkExceptions.PaymentLinkCreditFailed ex) {
                return buildErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                ex.getMessage(),
                                "ERR_PAYMENT_LINK_CREDIT_FAILED");
        }

        // ============================================
        // MINING
        // ============================================

        @ExceptionHandler(MiningExceptions.RigNotFound.class)
        public ResponseEntity<ApiResponse<Void>> handleRigNotFound(MiningExceptions.RigNotFound ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                ex.getMessage(),
                                "ERR_MINING_RIG_NOT_FOUND");
        }

        @ExceptionHandler(MiningExceptions.MiningAllocationNotFound.class)
        public ResponseEntity<ApiResponse<Void>> handleMiningAllocationNotFound(
                        MiningExceptions.MiningAllocationNotFound ex) {
                return buildErrorResponse(
                                HttpStatus.NOT_FOUND,
                                ex.getMessage(),
                                "ERR_MINING_ALLOCATION_NOT_FOUND");
        }

        @ExceptionHandler(MiningExceptions.InvalidMiningAllocation.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidMiningAllocation(
                        MiningExceptions.InvalidMiningAllocation ex) {
                return buildErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                "ERR_MINING_ALLOCATION_INVALID");
        }

        @ExceptionHandler(MiningExceptions.MiningAllocationStateException.class)
        public ResponseEntity<ApiResponse<Void>> handleMiningAllocationState(
                        MiningExceptions.MiningAllocationStateException ex) {
                return buildErrorResponse(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                "ERR_MINING_ALLOCATION_STATE");
        }

        @ExceptionHandler(PaymentException.class)
        public ResponseEntity<ApiResponse<Void>> handlePaymentException(PaymentException ex) {
                incrementFinancialMetric("payment_error", ex.getErrorCode(), "payment");
                return buildErrorResponse(
                                ex.getStatus(),
                                sanitizeMessage(ex.getMessage(), "Não foi possível concluir esta solicitação agora."),
                                ex.getErrorCode());
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
                log.error("[GlobalExceptionHandler] Unhandled exception: {} — {}", ex.getClass().getName(),
                                ex.getMessage(), ex);
                return buildErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal Server Error: An unexpected error occurred on our end. Our team has been notified.",
                                ErrorCodes.SYS_INTERNAL_ERROR);
        }

        private ResponseEntity<ApiResponse<Void>> buildErrorResponse(HttpStatus status, String message,
                        String errorCode) {
                return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode));
        }

        private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(HttpStatus status, String message,
                        String errorCode, T data) {
                return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode, data));
        }

        private void incrementFinancialMetric(String name, String outcome, String type) {
                if (financialMetrics == null) {
                        return;
                }
                try {
                        financialMetrics.increment(name, outcome, type);
                } catch (RuntimeException exception) {
                        log.warn("[GlobalExceptionHandler] Failed to increment financial metric {}: {}", name,
                                        exception.getMessage());
                }
        }

        private String sanitizeMessage(String rawMessage, String fallbackMessage) {
                if (rawMessage == null) {
                        return fallbackMessage;
                }

                String normalized = rawMessage.trim();
                if (normalized.isEmpty()) {
                        return fallbackMessage;
                }
                if (normalized.length() > 180) {
                        return fallbackMessage;
                }
                if (TECHNICAL_MESSAGE_PATTERN.matcher(normalized).find()) {
                        return fallbackMessage;
                }
                return normalized;
        }
}
