package com.kerosene.auth.application.service.devicekey;

/** Distinguishes device-key challenges by the operation that may consume them. */
public enum DeviceKeyChallengePurpose {
    /** Challenge may be used only to register a device key. */
    REGISTER_DEVICE_KEY,
    /** Challenge may be used only to authenticate with a registered device key. */
    AUTH_DEVICE_KEY
}
