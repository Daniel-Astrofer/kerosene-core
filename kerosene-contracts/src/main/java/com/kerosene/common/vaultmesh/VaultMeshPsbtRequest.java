package com.kerosene.common.vaultmesh;

/**
 * Request for vault-mesh Taproot / PSBT signing under Intent gate.
 *
 * <p>{@code commitIntent=false} is for CHANNELS→LND inject: Intent is already soft-reserved;
 * mesh signs without durable-consuming so open failure can still release.
 */
public record VaultMeshPsbtRequest(
        String intentId,
        String sessionId,
        String bucket,
        String destination,
        long amountSats,
        String psbtBase64,
        Boolean commitIntent
) {
    /** Defaults {@code commitIntent} to true (USERS / standalone spends). */
    public VaultMeshPsbtRequest(
            String intentId,
            String sessionId,
            String bucket,
            String destination,
            long amountSats,
            String psbtBase64) {
        this(intentId, sessionId, bucket, destination, amountSats, psbtBase64, Boolean.TRUE);
    }

    public boolean shouldCommitIntent() {
        return commitIntent == null || commitIntent;
    }
}
