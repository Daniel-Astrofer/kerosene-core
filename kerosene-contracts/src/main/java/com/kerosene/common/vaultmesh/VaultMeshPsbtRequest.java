package com.kerosene.common.vaultmesh;

/**
 * Request for vault-mesh Taproot / PSBT signing under Intent gate.
 */
public record VaultMeshPsbtRequest(
        String intentId,
        String sessionId,
        String bucket,
        String destination,
        long amountSats,
        String psbtBase64
) {
}
