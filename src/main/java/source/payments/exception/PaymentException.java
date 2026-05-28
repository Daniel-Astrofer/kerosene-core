package source.payments.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public PaymentException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static PaymentException badRequest(String errorCode, String message) {
        return new PaymentException(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public static PaymentException conflict(String errorCode, String message) {
        return new PaymentException(errorCode, message, HttpStatus.CONFLICT);
    }

    public static PaymentException notFound(String errorCode, String message) {
        return new PaymentException(errorCode, message, HttpStatus.NOT_FOUND);
    }

    public static PaymentException unavailable(String errorCode, String message) {
        return new PaymentException(errorCode, message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
