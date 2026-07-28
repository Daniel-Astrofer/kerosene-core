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
    public VaultMeshReshareResult {
        if (ok && error != null) throw new IllegalArgumentException("ok and error cannot both be set");
    }

    public static VaultMeshReshareResult ok(String policy, String reason) {
        if (policy == null || policy.isBlank()) throw new IllegalArgumentException("policy required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
        return new VaultMeshReshareResult(true, policy, reason, true, null);
    }

    public static VaultMeshReshareResult failed(String error) {
        if (error == null || error.isBlank()) throw new IllegalArgumentException("error required");
        return new VaultMeshReshareResult(false, null, null, false, error);
    }
}
