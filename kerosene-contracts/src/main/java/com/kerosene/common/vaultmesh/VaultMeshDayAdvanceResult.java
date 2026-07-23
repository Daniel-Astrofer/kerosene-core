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
    public static VaultMeshDayAdvanceResult ok(String dayEpoch, boolean advanced) {
        return new VaultMeshDayAdvanceResult(dayEpoch, advanced, true, null);
    }

    public static VaultMeshDayAdvanceResult failed(String error) {
        return new VaultMeshDayAdvanceResult(null, false, false, error);
    }
}
