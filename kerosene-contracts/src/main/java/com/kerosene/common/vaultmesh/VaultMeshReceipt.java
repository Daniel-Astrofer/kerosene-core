package com.kerosene.common.vaultmesh;

import java.time.Instant;

/**
 * Settlement receipt with status, txid, and metadata from vault mesh.
 *
 * <p>Produced by the vault mesh after processing a {@link VaultMeshIntent}.
 * Status indicates acceptance, rejection, or fail-stop. On acceptance,
 * {@code txidOrProof} carries the Bitcoin transaction id.</p>
 *
 * @since 0.1.0
 */
public record VaultMeshReceipt(
        String intentId,
        Status status,
        String reasonCode,
        String txidOrProof,
        Instant completedAt
) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        FAIL_STOP
    }

    public VaultMeshReceipt {
        if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
        if (status == null) throw new IllegalArgumentException("status required");
        if (completedAt == null) throw new IllegalArgumentException("completedAt required");
    }
}
