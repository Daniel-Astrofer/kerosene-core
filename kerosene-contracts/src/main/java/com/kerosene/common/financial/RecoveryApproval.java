package com.kerosene.common.financial;

import java.time.Instant;

/**
 * Recovery proof for emergency access flow.
 * NEVER log or serialize raw fields.
 */
public record RecoveryApproval(
    String proof,
    String challengeNonce,
    Instant generatedAt
) {
    public RecoveryApproval {
        if (proof == null || proof.isBlank()) throw new IllegalArgumentException("proof required");
        if (challengeNonce == null || challengeNonce.isBlank()) throw new IllegalArgumentException("challengeNonce required");
        if (generatedAt == null) throw new IllegalArgumentException("generatedAt required");
    }

    @Override
    public String toString() {
        return "RecoveryApproval[challengeNonce=" + challengeNonce + ", generatedAt=" + generatedAt + "]";
    }
}
