package com.kerosene.common.financial;

import java.time.Instant;
import java.util.UUID;

public interface FinancialMpcKeyPort {

    MpcWalletKeyReceipt provisionWalletKey(MpcWalletKeyRequest request);

    record MpcWalletKeyRequest(
        UUID walletId,
        Long userId,
        String scheme,         // FROST, DKG, etc.
        String network,        // MAINNET, TESTNET
        String purpose         // INBOUND, OUTBOUND, CHANGE
    ) {
        public MpcWalletKeyRequest {
            if (walletId == null) throw new IllegalArgumentException("walletId required");
            if (userId == null) throw new IllegalArgumentException("userId required");
            if (scheme == null || scheme.isBlank()) throw new IllegalArgumentException("scheme required");
            if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
            if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose required");
        }
    }

    record MpcWalletKeyReceipt(
        String keyId,                  // unique key identifier
        UUID walletId,
        String scheme,                 // FROST, DKG, etc.
        String network,                // MAINNET, TESTNET
        String xOnlyPublicKey,         // 64 hex chars — Taproot x-only pubkey
        String descriptor,             // Bitcoin output descriptor
        String address,                // derived Bitcoin address (for convenience)
        String constitutionHash,       // constitution that authorized this key
        Instant createdAt,             // when the key was provisioned
        String attestation             // cryptographic attestation from the MPC protocol
    ) {
        public MpcWalletKeyReceipt {
            if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId required");
            if (walletId == null) throw new IllegalArgumentException("walletId required");
            if (scheme == null || scheme.isBlank()) throw new IllegalArgumentException("scheme required");
            if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
            if (xOnlyPublicKey == null || xOnlyPublicKey.isBlank()) throw new IllegalArgumentException("xOnlyPublicKey required");
            if (descriptor == null || descriptor.isBlank()) throw new IllegalArgumentException("descriptor required");
            if (address == null || address.isBlank()) throw new IllegalArgumentException("address required");
            if (constitutionHash == null || constitutionHash.isBlank()) throw new IllegalArgumentException("constitutionHash required");
            if (createdAt == null) throw new IllegalArgumentException("createdAt required");
            if (attestation == null || attestation.isBlank()) throw new IllegalArgumentException("attestation required");
        }
    }

    /**
     * @deprecated Ambiguous: returns raw string instead of typed receipt with attestation.
     *             Use {@link #provisionWalletKey(MpcWalletKeyRequest)} instead.
     */
    @Deprecated(forRemoval = true)
    default String keygenWallet(UUID walletId, Long userId) {
        throw new UnsupportedOperationException("Use provisionWalletKey(MpcWalletKeyRequest) instead.");
    }
}
