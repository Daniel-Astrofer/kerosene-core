package source.common.exception;

public class VaultException extends KeroseneException {

    public VaultException(String message, String errorCode) {
        super(message, errorCode);
    }

    public VaultException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }
}
