package source.auth.dto.devicekey;

public record DeviceKeyChallengeResponse(
        String challengeId,
        String challenge,
        long expiresInSeconds,
        String onionServiceId,
        String algorithm,
        String canonicalization) {
}
