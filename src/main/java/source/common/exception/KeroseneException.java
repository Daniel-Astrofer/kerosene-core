package source.common.exception;

public abstract class KeroseneException extends RuntimeException {

    private final String errorCode;

    public KeroseneException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public KeroseneException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
