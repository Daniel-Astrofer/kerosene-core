package source.auth.application.service.devicekey;

public record DeviceKeyChallengeState(
        String challengeId,
        String challenge,
        DeviceKeyChallengePurpose purpose,
        String username,
        Long userId,
        String sessionId,
        long expiresAtEpochSeconds) {
}
