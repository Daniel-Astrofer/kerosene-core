package com.kerosene.common.vaultmesh;

/**
 * Snapshot of vault-mesh day_epoch relative to the caller's UTC calendar day.
 */
public record VaultMeshDayStatus(
        String dayEpoch,
        String neededDayEpoch,
        boolean upToDate,
        boolean stale,
        String error
) {
    public static VaultMeshDayStatus upToDate(String dayEpoch) {
        return new VaultMeshDayStatus(dayEpoch, dayEpoch, true, false, null);
    }

    public static VaultMeshDayStatus stale(String have, String need) {
        return new VaultMeshDayStatus(have, need, false, true, null);
    }

    public static VaultMeshDayStatus failed(String error) {
        return new VaultMeshDayStatus(null, null, false, false, error);
    }
}
