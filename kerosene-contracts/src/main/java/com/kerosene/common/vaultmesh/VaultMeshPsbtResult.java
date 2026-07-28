package com.kerosene.common.vaultmesh;

import java.time.Instant;

/**
 * Sealed hierarchy for PSBT signing results.
 * ACCEPTED must carry all verification fields.
 * REJECTED must explain why.
 * FAIL_STOP must indicate catastrophic halt.
 */
public sealed interface VaultMeshPsbtResult
    permits VaultMeshPsbtResult.AcceptedPsbt,
            VaultMeshPsbtResult.RejectedPsbt,
            VaultMeshPsbtResult.FailStopPsbt {

    record AcceptedPsbt(
        String intentId,
        String proposalHash,
        String psbtHash,
        String signedPsbtBase64,
        String constitutionHash,
        long constitutionEpoch,
        String[] participantIds,
        int threshold,
        String transcriptHash,
        String aggregateProof,
        String signerKeyId,
        String network,
        String bucket,
        long amountSats,
        String destination,
        Instant completedAt,
        Instant expiresAt
    ) implements VaultMeshPsbtResult {
        public AcceptedPsbt {
            if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
            if (proposalHash == null || proposalHash.isBlank()) throw new IllegalArgumentException("proposalHash required");
            if (signedPsbtBase64 == null || signedPsbtBase64.isBlank()) throw new IllegalArgumentException("signedPsbtBase64 required");
            if (constitutionHash == null || constitutionHash.isBlank()) throw new IllegalArgumentException("constitutionHash required");
            if (participantIds == null || participantIds.length < threshold) throw new IllegalArgumentException("insufficient participants");
            if (threshold < 1) throw new IllegalArgumentException("threshold must be >= 1");
            if (aggregateProof == null || aggregateProof.isBlank()) throw new IllegalArgumentException("aggregateProof required");
            if (signerKeyId == null || signerKeyId.isBlank()) throw new IllegalArgumentException("signerKeyId required");
            if (completedAt == null) throw new IllegalArgumentException("completedAt required");
        }
    }

    record RejectedPsbt(
        String intentId,
        String proposalHash,
        String reasonCode,
        String reasonDetail,
        Instant completedAt
    ) implements VaultMeshPsbtResult {
        public RejectedPsbt {
            if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
            if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode required");
            if (completedAt == null) throw new IllegalArgumentException("completedAt required");
        }
    }

    record FailStopPsbt(
        String intentId,
        String reasonCode,
        String reasonDetail,
        Instant completedAt
    ) implements VaultMeshPsbtResult {
        public FailStopPsbt {
            if (intentId == null || intentId.isBlank()) throw new IllegalArgumentException("intentId required");
            if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode required");
            if (completedAt == null) throw new IllegalArgumentException("completedAt required");
        }
    }
}
