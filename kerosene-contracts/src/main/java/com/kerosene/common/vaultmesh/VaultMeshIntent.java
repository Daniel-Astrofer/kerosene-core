package com.kerosene.common.vaultmesh;

import java.time.Instant;

/**
 * Settlement intent model between KFE ledger and vault mesh custody plane.
 *
 * <p>Fire-and-forget from the bank: no FROST shares on the JVM. The intent carries
 * the settlement destination, amount, policy hash, and optional hybrid (Ed25519 + ML-DSA-65)
 * signatures for PQ-safe authorization. The vault mesh validates signatures (AND logic)
 * before executing.</p>
 *
 * @since 0.1.0
 */
public record VaultMeshIntent(
        String intentId,
        String bucket,
        String destination,
        long amountSats,
        String policyHash,
        Instant createdAt,
        /** Ed25519 raw signature over canonical intent hash (hex-encoded). */
        String ed25519SignatureHex,
        /** ML-DSA-65 raw signature over canonical intent hash (hex-encoded). */
        String mlDsa65SignatureHex,
        /** Key identifier for the Ed25519 verification key (roster index). */
        String ed25519KeyId,
        /** Key identifier for the ML-DSA-65 verification key (roster index). */
        String mlDsaKeyId
) {
    public VaultMeshIntent {
        if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket required");
        if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0, got: " + amountSats);
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
    }
}
