package com.kerosene.common.vaultmesh;

/**
 * Outcome of {@code POST /v1/day/vote} or {@code POST /v1/day/advance}.
 */
public record VaultMeshDayAdvanceResult(
        String dayEpoch,
        boolean advanced,
        boolean ok,
        String error
) {
    public VaultMeshDayAdvanceResult {
        if (ok && error != null) throw new IllegalArgumentException("ok and error cannot both be set");
    }

    public static VaultMeshDayAdvanceResult ok(String dayEpoch, boolean advanced) {
        if (dayEpoch == null || dayEpoch.isBlank()) throw new IllegalArgumentException("dayEpoch required");
        return new VaultMeshDayAdvanceResult(dayEpoch, advanced, true, null);
    }

    public static VaultMeshDayAdvanceResult failed(String error) {
        if (error == null || error.isBlank()) throw new IllegalArgumentException("error required");
        return new VaultMeshDayAdvanceResult(null, false, false, error);
    }
}
