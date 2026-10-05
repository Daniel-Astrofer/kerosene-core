package com.kerosene.auth.dto.devicekey;

/**
 * Public response that binds a device-key signing challenge to its transport and protocol rules.
 * @param challengeId opaque identifier used to retrieve and consume this one-time challenge
 * @param challenge Base64URL-encoded random bytes that the client must sign
 * @param expiresInSeconds remaining lifetime advertised to the client
 * @param onionServiceId Tor service identity to include in the signed payload
 * @param algorithm signature algorithm required by the registered key
 * @param canonicalization payload canonicalization version required before signing
 */
public record DeviceKeyChallengeResponse(
        String challengeId,
        String challenge,
        long expiresInSeconds,
        String onionServiceId,
        String algorithm,
        String canonicalization) {
}
