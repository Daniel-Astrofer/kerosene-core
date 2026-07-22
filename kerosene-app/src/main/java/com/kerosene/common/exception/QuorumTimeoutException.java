package source.common.exception;

public class QuorumTimeoutException extends KeroseneException {

    public QuorumTimeoutException(String message, String errorCode) {
        super(message, errorCode);
    }

    public QuorumTimeoutException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }
}
