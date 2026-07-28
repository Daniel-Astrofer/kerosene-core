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
    public VaultMeshDayStatus {
        // error is the only non-null field when failed
        if (upToDate && stale) throw new IllegalArgumentException("upToDate and stale cannot both be true");
    }

    public static VaultMeshDayStatus upToDate(String dayEpoch) {
        if (dayEpoch == null || dayEpoch.isBlank()) throw new IllegalArgumentException("dayEpoch required");
        return new VaultMeshDayStatus(dayEpoch, dayEpoch, true, false, null);
    }

    public static VaultMeshDayStatus stale(String have, String need) {
        if (have == null || have.isBlank()) throw new IllegalArgumentException("have dayEpoch required");
        if (need == null || need.isBlank()) throw new IllegalArgumentException("need dayEpoch required");
        return new VaultMeshDayStatus(have, need, false, true, null);
    }

    public static VaultMeshDayStatus failed(String error) {
        if (error == null || error.isBlank()) throw new IllegalArgumentException("error required");
        return new VaultMeshDayStatus(null, null, false, false, error);
    }
}
