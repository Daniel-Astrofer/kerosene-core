package com.kerosene.common.vaultmesh;

/**
 * Result of vault-mesh PSBT signing (signed PSBT + policy receipt status).
 */
public record VaultMeshPsbtReceipt(
        String intentId,
        VaultMeshReceipt.Status status,
        String reasonCode,
        String signedPsbt,
        String signatureProof,
        long completedAtEpochMs
) {
}
