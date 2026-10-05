package com.kerosene.auth.application.service.devicekey;

/** Base unchecked failure for malformed, mismatched, or unsupported device-key protocol data. */
public class DeviceKeyProtocolException extends RuntimeException {
    /** Creates a protocol failure. */
    /** @param message validation detail safe for the caller's error mapping */
    public DeviceKeyProtocolException(String message) {
        super(message);
    }

    /** Creates a protocol failure while preserving its underlying cause. */
    /** @param message protocol failure description */
    /** @param cause parsing, crypto, or storage failure */
    public DeviceKeyProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
