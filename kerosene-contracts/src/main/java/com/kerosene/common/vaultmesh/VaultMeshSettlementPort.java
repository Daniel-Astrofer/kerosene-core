package com.kerosene.common.vaultmesh;

/**
 * Port for {@code kfe-service} to submit settlement intents to the vault mesh.
 * Implementations live in adapters only (Clean Architecture / DIP).
 *
 * @deprecated Split into focused interfaces:
 *             {@link VaultIntentPort},
 *             {@link VaultPsbtSigningPort},
 *             {@link VaultReservationPort},
 *             {@link VaultDepositDescriptorPort},
 *             {@link VaultGovernancePort}.
 */
@Deprecated
public interface VaultMeshSettlementPort {

    /** @deprecated Use {@link VaultIntentPort#submitIntent(VaultMeshIntentV2)}. */
    @Deprecated
    default VaultMeshReceipt submitIntent(VaultMeshIntent intent) {
        throw new UnsupportedOperationException("Use VaultIntentPort");
    }

    /** @deprecated Use {@link VaultReservationPort#reserveIntent(VaultMeshIntentV2)}. */
    @Deprecated
    default VaultMeshReceipt reserveIntent(VaultMeshIntent intent) {
        throw new UnsupportedOperationException("Use VaultReservationPort");
    }

    /** @deprecated Use {@link VaultReservationPort#releaseIntent(String, String)}. */
    @Deprecated
    default VaultMeshReceipt releaseIntent(String intentId, String bucket, long amountSats) {
        throw new UnsupportedOperationException("Use VaultReservationPort");
    }

    /** @deprecated Use {@link VaultReservationPort#commitIntent(String, String)}. */
    @Deprecated
    default VaultMeshReceipt commitIntent(String intentId) {
        throw new UnsupportedOperationException("Use VaultReservationPort");
    }

    /** @deprecated Use {@link VaultPsbtSigningPort#signPsbt(VaultMeshPsbtRequestV2)}. */
    @Deprecated
    default VaultMeshPsbtReceipt signPsbt(VaultMeshPsbtRequest request) {
        throw new UnsupportedOperationException("Use VaultPsbtSigningPort");
    }

    /** @deprecated Use {@link VaultGovernancePort#getDayStatus()}. */
    @Deprecated
    default VaultMeshDayStatus getDayStatus() {
        throw new UnsupportedOperationException("Use VaultGovernancePort");
    }

    /** @deprecated Use {@link VaultGovernancePort#voteDay(String, String)}. */
    @Deprecated
    default VaultMeshDayAdvanceResult voteDay(String voter, String dayEpoch) {
        throw new UnsupportedOperationException("Use VaultGovernancePort");
    }

    /** @deprecated Use {@link VaultGovernancePort#advanceDay()}. */
    @Deprecated
    default VaultMeshDayAdvanceResult advanceDay() {
        throw new UnsupportedOperationException("Use VaultGovernancePort");
    }

    /** @deprecated Use {@link VaultGovernancePort#triggerReshare(String)}. */
    @Deprecated
    default VaultMeshReshareResult triggerReshare(String reason) {
        throw new UnsupportedOperationException("Use VaultGovernancePort");
    }

    /** @deprecated Use {@link VaultDepositDescriptorPort#getUsersDepositAddress()}. */
    @Deprecated
    default VaultMeshDepositInfo getUsersDepositAddress() {
        throw new UnsupportedOperationException("Use VaultDepositDescriptorPort");
    }

    /** @deprecated Use {@link VaultDepositDescriptorPort#getChannelsDepositAddress()}. */
    @Deprecated
    default VaultMeshDepositInfo getChannelsDepositAddress() {
        throw new UnsupportedOperationException("Use VaultDepositDescriptorPort");
    }
}
