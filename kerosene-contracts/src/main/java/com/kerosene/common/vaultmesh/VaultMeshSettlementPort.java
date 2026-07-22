package com.kerosene.common.vaultmesh;

/**
 * Port for {@code kfe-service} to submit settlement intents to the vault mesh.
 * Implementations live in adapters only (Clean Architecture / DIP).
 */
public interface VaultMeshSettlementPort {

    VaultMeshReceipt submitIntent(VaultMeshIntent intent);
}
