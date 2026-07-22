package source.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import source.auth.AuthExceptions;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestResponseErrors extends ResponseEntityExceptionHandler {

        @ExceptionHandler(AuthExceptions.UserAlreadyExistsException.class)
        public ResponseEntity<ApiResponse<Void>> userAlreadyExists(Exception ex,
                        HttpServletRequest request) {
                return error(HttpStatus.CONFLICT,
                                "A user with this username already exists.",
                                ErrorCodes.AUTH_USER_ALREADY_EXISTS);
        }

        @ExceptionHandler(AuthExceptions.UserNoExists.class)
        public ResponseEntity<ApiResponse<Void>> userNoExists(Exception ex,
                        HttpServletRequest request) {
                return error(HttpStatus.NOT_FOUND,
                                "No account could be found for the provided username.",
                                ErrorCodes.AUTH_USER_NOT_FOUND);
        }

        @ExceptionHandler(AuthExceptions.incorrectTotp.class)
        public ResponseEntity<ApiResponse<Void>> incorretTotp(AuthExceptions.incorrectTotp ex,
                        HttpServletRequest request) {
                return error(HttpStatus.UNAUTHORIZED,
                                "The provided TOTP code is incorrect or expired.",
                                ErrorCodes.AUTH_INCORRECT_TOTP);

        }

        @ExceptionHandler(AuthExceptions.InvalidCredentials.class)
        public ResponseEntity<ApiResponse<Void>> invalidCredentials(AuthExceptions.InvalidCredentials ex,
                        HttpServletRequest request) {
                return error(HttpStatus.UNAUTHORIZED,
                                "Invalid credentials provided.",
                                ErrorCodes.AUTH_INVALID_CREDENTIALS);

        }

        @ExceptionHandler(AuthExceptions.UnrrecognizedDevice.class)
        public ResponseEntity<ApiResponse<Void>> invalidSession(AuthExceptions.UnrrecognizedDevice ex,
                        HttpServletRequest request) {
                return error(HttpStatus.FORBIDDEN,
                                "Unrecognized device detected.",
                                ErrorCodes.AUTH_UNRECOGNIZED_DEVICE);
        }

        @ExceptionHandler
        public ResponseEntity<ApiResponse<Void>> TotpTimeExceded(AuthExceptions.TotpTimeExceded ex,
                        HttpServletRequest request) {
                return error(HttpStatus.REQUEST_TIMEOUT,
                                "The time limit for TOTP verification has been exceeded.",
                                ErrorCodes.AUTH_TOTP_TIMEOUT);
        }

        private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message, String errorCode) {
                return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode));
        }
}
