package com.kerosene.common.vaultmesh;

/**
 * Port for {@code kfe-service} to submit settlement intents to the vault mesh.
 * Implementations live in adapters only (Clean Architecture / DIP).
 */
public interface VaultMeshSettlementPort {

    VaultMeshReceipt submitIntent(VaultMeshIntent intent);

    /**
     * Intent-gated Bitcoin PSBT signing (Taproot key-path via frost-secp256k1-tr).
     * Default rejects so non-mesh adapters stay fail-closed.
     */
    default VaultMeshPsbtReceipt signPsbt(VaultMeshPsbtRequest request) {
        return new VaultMeshPsbtReceipt(
                request == null ? null : request.intentId(),
                VaultMeshReceipt.Status.REJECTED,
                "MESH_PSBT_UNSUPPORTED",
                null,
                null,
                System.currentTimeMillis());
    }
}
