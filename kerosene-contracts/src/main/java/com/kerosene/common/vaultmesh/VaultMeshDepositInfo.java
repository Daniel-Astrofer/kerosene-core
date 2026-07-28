package com.kerosene.common.vaultmesh;

/**
 * Response model for {@code GET /v1/bitcoin/deposit}.
 *
 * <p>Shared Taproot deposit derived from the mesh Taproot group verifying key.
 */
public record VaultMeshDepositInfo(
        String address,
        String descriptor,
        String scheme,
        String outputPubkeyHex,
        String xonlyPubkeyHex,
        String network) {

    public VaultMeshDepositInfo {
        if (address == null || address.isBlank()) throw new IllegalArgumentException("address required");
        if (scheme == null || scheme.isBlank()) throw new IllegalArgumentException("scheme required");
        if (outputPubkeyHex == null || outputPubkeyHex.isBlank()) throw new IllegalArgumentException("outputPubkeyHex required");
        if (xonlyPubkeyHex != null && xonlyPubkeyHex.isBlank()) {
            throw new IllegalArgumentException("xonlyPubkeyHex must be null or non-blank");
        }
        if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
    }
}
