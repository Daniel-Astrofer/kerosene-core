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
        String network) {}

