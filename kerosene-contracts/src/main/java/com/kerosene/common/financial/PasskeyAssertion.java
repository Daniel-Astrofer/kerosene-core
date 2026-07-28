package com.kerosene.common.financial;

import java.time.Instant;

/**
 * Passkey (WebAuthn) assertion for MFA approval.
 * NEVER log or serialize raw fields. Use {@link #toString()} for summaries.
 */
public record PasskeyAssertion(
    String credentialId,
    String clientDataJson,
    String authenticatorData,
    String signature,
    String userHandle
) {
    public PasskeyAssertion {
        if (credentialId == null || credentialId.isBlank()) throw new IllegalArgumentException("credentialId required");
        if (clientDataJson == null || clientDataJson.isBlank()) throw new IllegalArgumentException("clientDataJson required");
        if (authenticatorData == null || authenticatorData.isBlank()) throw new IllegalArgumentException("authenticatorData required");
        if (signature == null || signature.isBlank()) throw new IllegalArgumentException("signature required");
    }

    @Override
    public String toString() {
        return "PasskeyAssertion[credentialId=" + credentialId + ", userHandle=" + (userHandle != null ? "***" : "null") + "]";
    }
}
