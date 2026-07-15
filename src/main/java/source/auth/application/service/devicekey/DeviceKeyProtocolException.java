package source.auth.application.service.devicekey;

public class DeviceKeyProtocolException extends RuntimeException {
    public DeviceKeyProtocolException(String message) {
        super(message);
    }

    public DeviceKeyProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
