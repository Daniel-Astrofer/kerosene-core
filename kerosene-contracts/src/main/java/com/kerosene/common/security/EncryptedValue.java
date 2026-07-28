package com.kerosene.common.security;

import java.time.Instant;

/**
 * Immutable encrypted payload with AAD context binding.
 * The master key never leaves the KMS/HSM boundary.
 */
public record EncryptedValue(
    String keyId,              // KMS key identifier
    String algorithm,          // e.g. AES-256-GCM
    int version,               // encryption scheme version
    byte[] nonce,              // 12 bytes for GCM
    byte[] ciphertext,         // encrypted payload
    byte[] authenticationTag,  // 16 bytes for GCM
    Instant createdAt           // when encryption occurred
) {
    public EncryptedValue {
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId required");
        if (algorithm == null || algorithm.isBlank()) throw new IllegalArgumentException("algorithm required");
        if (nonce == null || nonce.length == 0) throw new IllegalArgumentException("nonce required");
        if (ciphertext == null || ciphertext.length == 0) throw new IllegalArgumentException("ciphertext required");
        if (authenticationTag == null || authenticationTag.length == 0) throw new IllegalArgumentException("authenticationTag required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
    }
}
