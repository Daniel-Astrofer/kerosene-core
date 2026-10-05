package com.kerosene.auth.dto;

/**
 * TOTP enrollment material returned while a user configures an authenticator application.
 * @param otpUri provisioning URI that encodes issuer, account, and secret for authenticator setup
 * @param secret shared Base32 seed displayed or encoded in the enrollment QR code
 */
public record TotpSetupResponseDTO(
        String otpUri,
        String secret) {
}
