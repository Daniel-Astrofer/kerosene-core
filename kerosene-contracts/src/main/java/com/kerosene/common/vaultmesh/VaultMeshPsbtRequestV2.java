package com.kerosene.common.vaultmesh;

import java.time.Instant;

/**
 * PSBT signing request cryptographically bound to an intent.
 * The vault MUST reject if the PSBT doesn't match the intent.
 */
public record VaultMeshPsbtRequestV2(
    String intentId,
    String intentHash,
    String unsignedPsbtHash,
    String sessionId,
    String psbtBase64,
    String network,
    String policyHash,
    String constitutionHash,
    long constitutionEpoch,
    long maxFeeSats,
    String[] allowedOutputDescriptors,
    String changeDescriptor,
    int expectedInputCount,
    String nonce,
    Instant expiresAt,
    IntentCommitMode commitMode,
    HybridAuthorization userAuthorization
) {
    public VaultMeshPsbtRequestV2 {
        if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
        if (intentHash == null || intentHash.isBlank()) throw new IllegalArgumentException("intentHash required");
        if (unsignedPsbtHash == null || unsignedPsbtHash.isBlank()) throw new IllegalArgumentException("unsignedPsbtHash required");
        if (psbtBase64 == null || psbtBase64.isBlank()) throw new IllegalArgumentException("psbtBase64 required");
        if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
        if (policyHash == null || policyHash.isBlank()) throw new IllegalArgumentException("policyHash required");
        if (constitutionHash == null || constitutionHash.isBlank()) throw new IllegalArgumentException("constitutionHash required");
        if (maxFeeSats < 0) throw new IllegalArgumentException("maxFeeSats must be >= 0");
        if (expectedInputCount < 1) throw new IllegalArgumentException("expectedInputCount must be >= 1");
        if (nonce == null || nonce.isBlank()) throw new IllegalArgumentException("nonce required");
        if (commitMode == null) throw new IllegalArgumentException("commitMode required");
    }
}
