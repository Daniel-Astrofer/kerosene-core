package com.kerosene.common.financial;

import java.time.Instant;

/**
 * Device-bound proof for trusted-device MFA factor.
 * NEVER log or serialize raw fields.
 */
public record DeviceProof(
    String deviceId,
    String proof,
    String challengeNonce,
    Instant generatedAt
) {
    public DeviceProof {
        if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("deviceId required");
        if (proof == null || proof.isBlank()) throw new IllegalArgumentException("proof required");
        if (challengeNonce == null || challengeNonce.isBlank()) throw new IllegalArgumentException("challengeNonce required");
        if (generatedAt == null) throw new IllegalArgumentException("generatedAt required");
    }

    @Override
    public String toString() {
        return "DeviceProof[deviceId=" + deviceId + ", challengeNonce=" + challengeNonce + ", generatedAt=" + generatedAt + "]";
    }
}
