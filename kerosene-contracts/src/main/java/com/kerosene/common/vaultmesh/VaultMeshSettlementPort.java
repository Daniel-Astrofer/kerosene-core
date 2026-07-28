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
    VaultMeshReceipt submitIntent(VaultMeshIntent intent);

    /** @deprecated Use {@link VaultReservationPort#reserveIntent(VaultMeshIntentV2)}. */
    @Deprecated
    default VaultMeshReceipt reserveIntent(VaultMeshIntent intent) {
        return new VaultMeshReceipt(intent == null ? "unknown" : intent.intentId(),
                VaultMeshReceipt.Status.REJECTED, "MESH_INTENT_RESERVE_UNSUPPORTED",
                null, java.time.Instant.now());
    }

    /** @deprecated Use {@link VaultReservationPort#releaseIntent(String, String)}. */
    @Deprecated
    default VaultMeshReceipt releaseIntent(String intentId, String bucket, long amountSats) {
        return new VaultMeshReceipt(intentId, VaultMeshReceipt.Status.REJECTED,
                "MESH_INTENT_RELEASE_UNSUPPORTED", null, java.time.Instant.now());
    }

    /** @deprecated Use {@link VaultReservationPort#commitIntent(String, String)}. */
    @Deprecated
    default VaultMeshReceipt commitIntent(String intentId) {
        return new VaultMeshReceipt(intentId, VaultMeshReceipt.Status.REJECTED,
                "MESH_INTENT_COMMIT_UNSUPPORTED", null, java.time.Instant.now());
    }

    /** @deprecated Use {@link VaultPsbtSigningPort#signPsbt(VaultMeshPsbtRequestV2)}. */
    @Deprecated
    default VaultMeshPsbtReceipt signPsbt(VaultMeshPsbtRequest request) {
        return new VaultMeshPsbtReceipt(request == null ? "unknown" : request.intentId(),
                VaultMeshReceipt.Status.REJECTED, "MESH_PSBT_UNSUPPORTED",
                null, null, java.time.Instant.now());
    }

    /** @deprecated Use {@link VaultGovernancePort#getDayStatus()}. */
    @Deprecated
    default VaultMeshDayStatus getDayStatus() {
        return VaultMeshDayStatus.failed("MESH_DAY_UNSUPPORTED");
    }

    /** @deprecated Use {@link VaultGovernancePort#voteDay(String, String)}. */
    @Deprecated
    default VaultMeshDayAdvanceResult voteDay(String voter, String dayEpoch) {
        return VaultMeshDayAdvanceResult.failed("MESH_DAY_UNSUPPORTED");
    }

    /** @deprecated Use {@link VaultGovernancePort#advanceDay()}. */
    @Deprecated
    default VaultMeshDayAdvanceResult advanceDay() {
        return VaultMeshDayAdvanceResult.failed("MESH_DAY_UNSUPPORTED");
    }

    /** @deprecated Use {@link VaultGovernancePort#triggerReshare(String)}. */
    @Deprecated
    default VaultMeshReshareResult triggerReshare(String reason) {
        return VaultMeshReshareResult.failed("MESH_RESHARE_UNSUPPORTED");
    }

    /** @deprecated Use {@link VaultDepositDescriptorPort#getUsersDepositAddress()}. */
    @Deprecated
    default VaultMeshDepositInfo getUsersDepositAddress() {
        return null;
    }

    /** @deprecated Use {@link VaultDepositDescriptorPort#getChannelsDepositAddress()}. */
    @Deprecated
    default VaultMeshDepositInfo getChannelsDepositAddress() {
        return null;
    }
}
