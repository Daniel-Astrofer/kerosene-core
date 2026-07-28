package com.kerosene.common.vaultmesh;

public interface VaultGovernancePort {
    VaultMeshDayStatus getDayStatus();
    VaultMeshDayAdvanceResult voteDay(String proposalHash, String signature);
    VaultMeshDayAdvanceResult advanceDay();
    VaultMeshReshareResult triggerReshare(String reason);
}
