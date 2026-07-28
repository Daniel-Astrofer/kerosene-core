package com.kerosene.common.vaultmesh;

public interface VaultPsbtSigningPort {
    VaultMeshPsbtResult signPsbt(VaultMeshPsbtRequestV2 request);
}
