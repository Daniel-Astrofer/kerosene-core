package source.common.exception;

import org.springframework.http.HttpStatus;

public class StructuredPlatformException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Object data;

    public StructuredPlatformException(String message, HttpStatus status, String errorCode, Object data) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }
}
