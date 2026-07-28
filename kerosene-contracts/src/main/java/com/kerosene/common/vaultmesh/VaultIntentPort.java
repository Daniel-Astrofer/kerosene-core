package com.kerosene.common.vaultmesh;

public interface VaultIntentPort {
    VaultMeshReceipt submitIntent(VaultMeshIntentV2 intent);
}
