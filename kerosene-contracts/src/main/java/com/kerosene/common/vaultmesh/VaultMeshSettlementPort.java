package com.kerosene.common.vaultmesh;

/**
 * Port for {@code kfe-service} to submit settlement intents to the vault mesh.
 * Implementations live in adapters only (Clean Architecture / DIP).
 */
public interface VaultMeshSettlementPort {

    VaultMeshReceipt submitIntent(VaultMeshIntent intent);

    /**
     * Intent-gated Bitcoin PSBT signing (Taproot key-path via frost-secp256k1-tr).
     * Default rejects so non-mesh adapters stay fail-closed.
     */
    default VaultMeshPsbtReceipt signPsbt(VaultMeshPsbtRequest request) {
        return new VaultMeshPsbtReceipt(
                request == null ? null : request.intentId(),
                VaultMeshReceipt.Status.REJECTED,
                "MESH_PSBT_UNSUPPORTED",
                null,
                null,
                System.currentTimeMillis());
    }

    /**
     * {@code GET /v1/day/current} — mesh ledger day vs UTC calendar (stale → not up-to-date).
     */
    default VaultMeshDayStatus getDayStatus() {
        return VaultMeshDayStatus.failed("MESH_DAY_UNSUPPORTED");
    }

    /**
     * {@code POST /v1/day/vote} — record a governance vote for {@code dayEpoch} ({@code YYYY-MM-DD}).
     */
    default VaultMeshDayAdvanceResult voteDay(String voter, String dayEpoch) {
        return VaultMeshDayAdvanceResult.failed("MESH_DAY_UNSUPPORTED");
    }

    /**
     * {@code POST /v1/day/advance} — advance ledger day when quorum is met (triggers daily reshare hook).
     */
    default VaultMeshDayAdvanceResult advanceDay() {
        return VaultMeshDayAdvanceResult.failed("MESH_DAY_UNSUPPORTED");
    }

    /**
     * {@code POST /v1/reshare/trigger} — explicit FROST reshare (manual policy or ops).
     */
    default VaultMeshReshareResult triggerReshare(String reason) {
        return VaultMeshReshareResult.failed("MESH_RESHARE_UNSUPPORTED");
    }

    /**
     * {@code GET /v1/bitcoin/deposit} — shared Taproot deposit address used for USERS deposits.
     *
     * <p>Product policy: users must deposit to shared Taproot group key ({@code tb1p} / {@code tr()}),
     * not to any xpub-derived receive address.
     */
    default VaultMeshDepositInfo getUsersDepositAddress() {
        return null;
    }
}
