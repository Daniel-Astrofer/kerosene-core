package com.kerosene.auth.application.service.devicekey;

/**
 * Redis-serialized owner, purpose, and expiry data for a one-time device-key challenge.
 * @param challengeId random identifier used to address the Redis record
 * @param challenge random proof value signed by the device
 * @param purpose registration or authentication operation
 * @param username normalized account name
 * @param userId account ID when challenge is for an existing account
 * @param sessionId signup session for onboarding registration
 * @param expiresAtEpochSeconds absolute expiration timestamp
 */
public record DeviceKeyChallengeState(
        String challengeId,
        String challenge,
        DeviceKeyChallengePurpose purpose,
        String username,
        Long userId,
        String sessionId,
        long expiresAtEpochSeconds) {
}
