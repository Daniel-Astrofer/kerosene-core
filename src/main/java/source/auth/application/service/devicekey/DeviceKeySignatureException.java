package source.auth.application.service.devicekey;

public class DeviceKeySignatureException extends DeviceKeyProtocolException {
    public DeviceKeySignatureException(String message) {
        super(message);
    }

    public DeviceKeySignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
