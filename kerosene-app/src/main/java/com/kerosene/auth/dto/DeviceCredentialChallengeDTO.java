package com.kerosene.auth.dto;

/**
 * Typed step-up challenge for a single device-credential factor.
 * {@code DEVICE_KEY} uses challengeId + KEROSENE_JSON_V1 fields;
 * {@code PASSKEY} uses WebAuthn-shaped hex challenge (legacy / clearnet path).
 */
public record DeviceCredentialChallengeDTO(
        String kind,
        String challengeId,
        String challenge,
        Long expiresInSeconds,
        String onionServiceId,
        String algorithm,
        String canonicalization) {

    public static DeviceCredentialChallengeDTO passkey(String challengeHex, long expiresInSeconds) {
        return new DeviceCredentialChallengeDTO(
                "PASSKEY",
                null,
                challengeHex,
                expiresInSeconds,
                null,
                null,
                null);
    }

    public static DeviceCredentialChallengeDTO deviceKey(
            String challengeId,
            String challenge,
            long expiresInSeconds,
            String onionServiceId,
            String algorithm,
            String canonicalization) {
        return new DeviceCredentialChallengeDTO(
                "DEVICE_KEY",
                challengeId,
                challenge,
                expiresInSeconds,
                onionServiceId,
                algorithm,
                canonicalization);
    }
}
