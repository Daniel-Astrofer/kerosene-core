package com.kerosene.common.vaultmesh;

import java.time.Instant;
import java.util.Optional;

public record VaultMeshIntentV2(
    int schemaVersion,
    String intentId,
    String network,
    String asset,
    String bucket,
    String destination,
    long amountSats,
    long maxFeeSats,
    String policyHash,
    String constitutionHash,
    long constitutionEpoch,
    String dayEpoch,
    String nonce,
    Instant issuedAt,
    Instant expiresAt,
    Optional<String> unsignedTransactionHash,
    HybridAuthorization authorization
) {
    public static final String DOMAIN_SEPARATOR = "KEROSENE_VAULT_INTENT_V2";

    public VaultMeshIntentV2 {
        if (schemaVersion != 2) throw new IllegalArgumentException("schemaVersion must be 2");
        if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
        if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
        if (asset == null || asset.isBlank()) throw new IllegalArgumentException("asset required");
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket required");
        if (destination == null || destination.isBlank()) throw new IllegalArgumentException("destination required");
        if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0");
        if (maxFeeSats < 0) throw new IllegalArgumentException("maxFeeSats must be >= 0");
        if (policyHash == null || policyHash.isBlank()) throw new IllegalArgumentException("policyHash required");
        if (constitutionHash == null || constitutionHash.isBlank()) throw new IllegalArgumentException("constitutionHash required");
        if (constitutionEpoch < 0) throw new IllegalArgumentException("constitutionEpoch must be >= 0");
        if (dayEpoch == null || dayEpoch.isBlank()) throw new IllegalArgumentException("dayEpoch required");
        if (nonce == null || nonce.isBlank()) throw new IllegalArgumentException("nonce required");
        if (issuedAt == null) throw new IllegalArgumentException("issuedAt required");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
        if (authorization == null) throw new IllegalArgumentException("authorization required");
    }
}
