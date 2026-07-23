package com.kerosene.common.vaultmesh;

/**
 * Port for {@code kfe-service} to submit settlement intents to the vault mesh.
 * Implementations live in adapters only (Clean Architecture / DIP).
 */
public interface VaultMeshSettlementPort {

    VaultMeshReceipt submitIntent(VaultMeshIntent intent);

    /**
     * Soft-reserve Intent capital ({@code POST /v1/intent/reserve}). Prefer for paths that
     * may fail after debit (e.g. CHANNELS→LND open): {@link #releaseIntent} on failure,
     * {@link #commitIntent} after success. Default rejects so non-mesh adapters stay fail-closed.
     */
    default VaultMeshReceipt reserveIntent(VaultMeshIntent intent) {
        return new VaultMeshReceipt(
                intent == null ? null : intent.intentId(),
                VaultMeshReceipt.Status.REJECTED,
                "MESH_INTENT_RESERVE_UNSUPPORTED",
                null,
                System.currentTimeMillis());
    }

    /**
     * Roll back a soft reservation ({@code POST /v1/intent/release}).
     */
    default VaultMeshReceipt releaseIntent(String intentId, String bucket, long amountSats) {
        return new VaultMeshReceipt(
                intentId,
                VaultMeshReceipt.Status.REJECTED,
                "MESH_INTENT_RELEASE_UNSUPPORTED",
                null,
                System.currentTimeMillis());
    }

    /**
     * Promote reservation → durable consume ({@code POST /v1/intent/commit}).
     */
    default VaultMeshReceipt commitIntent(String intentId) {
        return new VaultMeshReceipt(
                intentId,
                VaultMeshReceipt.Status.REJECTED,
                "MESH_INTENT_COMMIT_UNSUPPORTED",
                null,
                System.currentTimeMillis());
    }

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

    /**
     * {@code GET /v1/bitcoin/deposit?bucket=CHANNELS} — dedicated CHANNELS Taproot deposit
     * (≠ USERS omnibus key).
     */
    default VaultMeshDepositInfo getChannelsDepositAddress() {
        return null;
    }
}
