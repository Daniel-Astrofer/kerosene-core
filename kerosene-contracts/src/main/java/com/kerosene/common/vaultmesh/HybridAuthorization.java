package com.kerosene.common.vaultmesh;

/**
 * Dual Ed25519 + ML-DSA-65 (post-quantum) authorization.
 * Both signatures are REQUIRED for mainnet. Testnet may relax to single sig.
 */
public record HybridAuthorization(
    String ed25519SignatureHex,
    String mlDsa65SignatureHex,
    String ed25519KeyId,
    String mlDsaKeyId,
    boolean testnetRelaxed
) {
    public HybridAuthorization {
        if (ed25519SignatureHex == null || ed25519SignatureHex.isBlank())
            throw new IllegalArgumentException("ed25519Signature required");
        if (mlDsa65SignatureHex == null || mlDsa65SignatureHex.isBlank())
            throw new IllegalArgumentException("mlDsa65Signature required (use empty string for testnet)");
        if (ed25519KeyId == null || ed25519KeyId.isBlank())
            throw new IllegalArgumentException("ed25519KeyId required");
        if (mlDsaKeyId == null || mlDsaKeyId.isBlank())
            throw new IllegalArgumentException("mlDsaKeyId required");
    }
}
