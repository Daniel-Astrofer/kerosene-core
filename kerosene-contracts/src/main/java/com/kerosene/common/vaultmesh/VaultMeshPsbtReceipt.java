package com.kerosene.common.vaultmesh;

import java.time.Instant;

/**
 * Result of vault-mesh PSBT signing (signed PSBT + policy receipt status).
 */
public record VaultMeshPsbtReceipt(
        String intentId,
        VaultMeshReceipt.Status status,
        String reasonCode,
        String signedPsbt,
        String signatureProof,
        Instant completedAt
) {
    public VaultMeshPsbtReceipt {
        if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
        if (status == null) throw new IllegalArgumentException("status required");
        if (completedAt == null) throw new IllegalArgumentException("completedAt required");
    }
}
