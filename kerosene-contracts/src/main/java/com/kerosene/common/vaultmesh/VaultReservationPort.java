package com.kerosene.common.vaultmesh;

public interface VaultReservationPort {
    VaultMeshReceipt reserveIntent(VaultMeshIntentV2 intent);
    VaultMeshReceipt releaseIntent(String intentId, String reservationToken);
    VaultMeshReceipt commitIntent(String intentId, String reservationToken);
}
