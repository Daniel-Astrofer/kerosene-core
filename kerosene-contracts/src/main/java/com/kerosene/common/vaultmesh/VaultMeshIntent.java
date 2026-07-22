package com.kerosene.common.vaultmesh;

/**
 * Settlement intent submitted by {@code kfe-service} to the vault mesh (F0 stub).
 * Fire-and-forget from the bank: no FROST shares on the JVM.
 */
public record VaultMeshIntent(
        String intentId,
        String bucket,
        String destination,
        long amountSats,
        String policyHash,
        long createdAtEpochMs
) {
}
