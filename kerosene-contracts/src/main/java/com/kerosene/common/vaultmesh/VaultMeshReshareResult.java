package com.kerosene.common.vaultmesh;

/**
 * Outcome of {@code POST /v1/reshare/trigger}.
 */
public record VaultMeshReshareResult(
        boolean reshared,
        String policy,
        String reason,
        boolean ok,
        String error
) {
    public static VaultMeshReshareResult ok(String policy, String reason) {
        return new VaultMeshReshareResult(true, policy, reason, true, null);
    }

    public static VaultMeshReshareResult failed(String error) {
        return new VaultMeshReshareResult(false, null, null, false, error);
    }
}
