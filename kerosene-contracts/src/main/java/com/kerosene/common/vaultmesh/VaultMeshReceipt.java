package com.kerosene.common.vaultmesh;

/**
 * Outcome of a vault-mesh signing attempt for an intent (F0 stub).
 */
public record VaultMeshReceipt(
        String intentId,
        Status status,
        String reasonCode,
        String txidOrProof,
        long completedAtEpochMs
) {
    public enum Status {
        ACCEPTED,
        REJECTED,
        FAIL_STOP
    }
}
