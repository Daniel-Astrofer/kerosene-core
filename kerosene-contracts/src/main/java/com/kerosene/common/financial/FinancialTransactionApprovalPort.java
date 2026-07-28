package com.kerosene.common.financial;

import java.time.Instant;
import java.util.Set;

/**
 * Transactional approval bound to a specific outbound proposal.
 * Approval factors MUST be cryptographically linked to the transaction they authorize.
 */
public interface FinancialTransactionApprovalPort {

    /**
     * Approve an outbound transaction with full binding to the proposal.
     */
    ApprovalReceipt approveOutbound(OutboundApprovalChallenge challenge);

    /**
     * Challenge that binds approval factors to a specific transaction.
     */
    record OutboundApprovalChallenge(
        String proposalHash,
        String transactionId,
        String ownerUserId,
        String actorUserId,
        String walletId,
        String destinationHash,
        long amountSats,
        long feeLimitSats,
        String network,
        String rail,
        String nonce,
        Instant issuedAt,
        Instant expiresAt,
        Set<AuthenticationAssertion> assertions
    ) {
        public OutboundApprovalChallenge {
            if (proposalHash == null || proposalHash.isBlank()) throw new IllegalArgumentException("proposalHash required");
            if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("transactionId required");
            if (ownerUserId == null || ownerUserId.isBlank()) throw new IllegalArgumentException("ownerUserId required");
            if (actorUserId == null || actorUserId.isBlank()) throw new IllegalArgumentException("actorUserId required");
            if (walletId == null || walletId.isBlank()) throw new IllegalArgumentException("walletId required");
            if (amountSats <= 0) throw new IllegalArgumentException("amountSats must be > 0");
            if (feeLimitSats <= 0) throw new IllegalArgumentException("feeLimitSats must be > 0");
            if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
            if (rail == null || rail.isBlank()) throw new IllegalArgumentException("rail required");
            if (nonce == null || nonce.isBlank()) throw new IllegalArgumentException("nonce required");
            if (issuedAt == null) throw new IllegalArgumentException("issuedAt required");
            if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
            if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
            if (assertions == null || assertions.isEmpty()) throw new IllegalArgumentException("at least one assertion required");
        }
    }

    /**
     * Authentication assertion — proof from a specific factor.
     * Sealed to enforce type safety per factor kind.
     */
    sealed interface AuthenticationAssertion
        permits AuthenticationAssertion.PasskeyAssertion,
                AuthenticationAssertion.DeviceProofAssertion,
                AuthenticationAssertion.RecoveryAssertion,
                AuthenticationAssertion.CustodyAssertion {

        String factorType();

        record PasskeyAssertion(
            String credentialId,
            String clientDataJson,
            String authenticatorData,
            String signature
        ) implements AuthenticationAssertion {
            public PasskeyAssertion {
                if (credentialId == null || credentialId.isBlank()) throw new IllegalArgumentException("credentialId required");
                if (clientDataJson == null || clientDataJson.isBlank()) throw new IllegalArgumentException("clientDataJson required");
                if (authenticatorData == null || authenticatorData.isBlank()) throw new IllegalArgumentException("authenticatorData required");
                if (signature == null || signature.isBlank()) throw new IllegalArgumentException("signature required");
            }
            public String factorType() { return "PASSKEY"; }
        }

        record DeviceProofAssertion(
            String deviceId,
            String proof,
            String challengeNonce
        ) implements AuthenticationAssertion {
            public DeviceProofAssertion {
                if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("deviceId required");
                if (proof == null || proof.isBlank()) throw new IllegalArgumentException("proof required");
                if (challengeNonce == null || challengeNonce.isBlank()) throw new IllegalArgumentException("challengeNonce required");
            }
            public String factorType() { return "DEVICE_PROOF"; }
        }

        record RecoveryAssertion(
            String recoveryKeyId,
            String proof,
            String challengeNonce
        ) implements AuthenticationAssertion {
            public RecoveryAssertion {
                if (recoveryKeyId == null || recoveryKeyId.isBlank()) throw new IllegalArgumentException("recoveryKeyId required");
                if (proof == null || proof.isBlank()) throw new IllegalArgumentException("proof required");
                if (challengeNonce == null || challengeNonce.isBlank()) throw new IllegalArgumentException("challengeNonce required");
            }
            public String factorType() { return "RECOVERY"; }
        }

        record CustodyAssertion(
            String custodyProviderId,
            String proof,
            String challengeNonce
        ) implements AuthenticationAssertion {
            public CustodyAssertion {
                if (custodyProviderId == null || custodyProviderId.isBlank()) throw new IllegalArgumentException("custodyProviderId required");
                if (proof == null || proof.isBlank()) throw new IllegalArgumentException("proof required");
                if (challengeNonce == null || challengeNonce.isBlank()) throw new IllegalArgumentException("challengeNonce required");
            }
            public String factorType() { return "CUSTODY"; }
        }
    }

    /**
     * Verifiable approval receipt.
     */
    record ApprovalReceipt(
        String approvalId,
        String proposalHash,
        Set<String> approvedFactors,
        String policyVersion,
        Instant issuedAt,
        Instant expiresAt,
        String proof
    ) {
        public ApprovalReceipt {
            if (approvalId == null || approvalId.isBlank()) throw new IllegalArgumentException("approvalId required");
            if (proposalHash == null || proposalHash.isBlank()) throw new IllegalArgumentException("proposalHash required");
            if (approvedFactors == null || approvedFactors.isEmpty()) throw new IllegalArgumentException("at least one approved factor required");
            if (policyVersion == null || policyVersion.isBlank()) throw new IllegalArgumentException("policyVersion required");
            if (issuedAt == null) throw new IllegalArgumentException("issuedAt required");
            if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
            if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
            if (proof == null || proof.isBlank()) throw new IllegalArgumentException("proof required");
        }
    }

    @Deprecated(forRemoval = true)
    default void approveLocalFactor(Long userId, String deviceRef, String factor) {
        throw new UnsupportedOperationException("Use approveOutbound(OutboundApprovalChallenge) with AuthenticationAssertion types.");
    }

    @Deprecated(forRemoval = true)
    default void approveCustodyTransfer(Long userId, String assertion) {
        throw new UnsupportedOperationException("Use approveOutbound(OutboundApprovalChallenge) with CustodyAssertion.");
    }

    @Deprecated(forRemoval = true)
    default void approveWalletOutbound(Long actorUserId, Long ownerUserId, String factorA, String factorB, String factorC) {
        throw new UnsupportedOperationException("Use approveOutbound(OutboundApprovalChallenge) with appropriate assertions.");
    }

    @Deprecated(forRemoval = true)
    default void approveColdWalletPsbt(Long userId, String factor) {
        throw new UnsupportedOperationException("Use approveOutbound(OutboundApprovalChallenge) with cold wallet assertions.");
    }
}
