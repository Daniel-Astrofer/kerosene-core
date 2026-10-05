package com.kerosene.auth.application.usecase.devicekey;

import java.util.Locale;

/** Package-local username normalization shared by device-key registration flows. */
final class DeviceKeyUsernameSupport {

    /** Prevents construction of this stateless normalization helper. */
    private DeviceKeyUsernameSupport() {
    }

    /** Trims surrounding whitespace and lowercases with a locale-independent rule. */
    /** @param username submitted username, possibly {@code null} */
    /** @return empty string for null, otherwise trimmed lowercase username */
    static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
