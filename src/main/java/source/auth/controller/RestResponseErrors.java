package source.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import source.auth.AuthExceptions;
import source.auth.dto.ResponseError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import source.wallet.exceptions.WalletExceptions;

import java.time.LocalDateTime;

@ControllerAdvice
public class RestResponseErrors extends ResponseEntityExceptionHandler {

        @ExceptionHandler(AuthExceptions.UserAlreadyExistsException.class)
        public ResponseEntity<ResponseError> userAlredyExists(Exception ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ResponseError(
                                LocalDateTime.now(),
                                HttpStatus.CONFLICT,
                                "User already exists",
                                ex.getMessage(),
                                request.getRequestURI()));
        }

        @ExceptionHandler(AuthExceptions.UserNoExists.class)
        public ResponseEntity<ResponseError> userNoExists(Exception ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ResponseError(
                                LocalDateTime.now(),
                                HttpStatus.NOT_FOUND,
                                "User no exists",
                                ex.getMessage(),
                                request.getRequestURI()));
        }

        @ExceptionHandler(AuthExceptions.incorrectTotp.class)
        public ResponseEntity<ResponseError> incorretTotp(AuthExceptions.incorrectTotp ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ResponseError(
                                LocalDateTime.now(),
                                HttpStatus.FORBIDDEN,
                                "Incorret TOTP",
                                ex.getMessage(),
                                request.getRequestURI()));

        }

        @ExceptionHandler(AuthExceptions.InvalidCredentials.class)
        public ResponseEntity<ResponseError> invalidCredentials(AuthExceptions.InvalidCredentials ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ResponseError(
                                LocalDateTime.now(),
                                HttpStatus.UNAUTHORIZED,
                                "Invalid Credentials",
                                ex.getMessage(),
                                request.getRequestURI()));

        }

        @ExceptionHandler(AuthExceptions.UnrrecognizedDevice.class)
        public ResponseEntity<ResponseError> unrecognizedDevice(AuthExceptions.UnrrecognizedDevice ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("Next-Endpoint", "auth/login/totp/verify")
                                .body(
                                                new ResponseError(
                                                                LocalDateTime.now(),
                                                                HttpStatus.SEE_OTHER,
                                                                "Unrecognized Device",
                                                                ex.getMessage(),
                                                                request.getRequestURI()));
        }

        @ExceptionHandler
        public ResponseEntity<ResponseError> TotpTimeExceded(AuthExceptions.TotpTimeExceded ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.GONE).body(
                                new ResponseError(
                                                LocalDateTime.now(),
                                                HttpStatus.GONE,
                                                "account is no more available,signup again",
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        @ExceptionHandler
        public ResponseEntity<ResponseError> DeviceNotRecognized(WalletExceptions.CreateWalletException ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                new ResponseError(
                                                LocalDateTime.now(),
                                                HttpStatus.UNAUTHORIZED,
                                                "this device is not logged in account",
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        @ExceptionHandler
        public ResponseEntity<ResponseError> WalletNoExists(WalletExceptions.WalletNoExists ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                new ResponseError(
                                                LocalDateTime.now(),
                                                HttpStatus.NOT_FOUND,
                                                "wallet no exists",
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        @ExceptionHandler
        public ResponseEntity<ResponseError> WalletAlredyExists(WalletExceptions.WalletNameAlredyExists ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                new ResponseError(
                                                LocalDateTime.now(),
                                                HttpStatus.CONFLICT,
                                                "you are using this name",
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        @ExceptionHandler
        public ResponseEntity<ResponseError> PassphraseNotBIP39(AuthExceptions.InvalidPassphrase ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(
                                new ResponseError(
                                                LocalDateTime.now(),
                                                HttpStatus.NOT_ACCEPTABLE,
                                                "this passphrase is not BIP39",
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        /**
         * HTTP 409 Conflict — duplicate idempotency key.
         * The app must NOT auto-retry with the same key. A new payment intent
         * requires a new UUID as the idempotency key.
         */
        @ExceptionHandler(source.ledger.exceptions.LedgerExceptions.DuplicateTransactionException.class)
        public ResponseEntity<ResponseError> duplicateTransaction(
                        source.ledger.exceptions.LedgerExceptions.DuplicateTransactionException ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ResponseError(
                                LocalDateTime.now(),
                                HttpStatus.CONFLICT,
                                "Duplicate Transaction",
                                ex.getMessage(),
                                request.getRequestURI()));
        }

        /**
         * HTTP 422 Unprocessable Entity — request timestamp outside allowed window.
         * Indicates a potential replay attack or severe clock skew.
         */
        @ExceptionHandler(source.ledger.exceptions.LedgerExceptions.TransactionReplayException.class)
        public ResponseEntity<ResponseError> transactionReplay(
                        source.ledger.exceptions.LedgerExceptions.TransactionReplayException ex,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ResponseError(
                                LocalDateTime.now(),
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "Request Expired or Replayed",
                                ex.getMessage(),
                                request.getRequestURI()));
        }
}