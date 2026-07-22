package source.auth.application.service.devicebinding;

import source.auth.dto.devicebinding.DeviceAlreadyBoundDTO;
import source.common.exception.ErrorCodes;

/**
 * Raised when a deviceInstallId is owned by another account and confirmUnlinkDevice was not set.
 */
public class DeviceBindingException extends RuntimeException {

    private final String errorCode;
    private final DeviceAlreadyBoundDTO payload;

    public DeviceBindingException(DeviceAlreadyBoundDTO payload) {
        super(payload == null ? "Device already bound" : payload.message());
        this.errorCode = ErrorCodes.AUTH_DEVICE_ALREADY_BOUND;
        this.payload = payload;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public DeviceAlreadyBoundDTO getPayload() {
        return payload;
    }
}
