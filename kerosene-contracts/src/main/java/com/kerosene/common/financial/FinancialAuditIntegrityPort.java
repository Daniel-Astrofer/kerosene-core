package com.kerosene.common.financial;

import java.time.Instant;
import java.time.LocalDateTime;

public interface FinancialAuditIntegrityPort {

    AuditRoot currentRoot();
    InclusionProof inclusionProof(long sequenceNumber);
    ConsistencyProof consistencyProof(AuditRoot fromRoot, AuditRoot toRoot);

    record AuditRoot(
        int rootVersion,          // schema version
        String hashAlgorithm,     // e.g. SHA-256
        String merkleRoot,        // hex-encoded root hash
        long eventCount,          // total events in tree
        long fromSequence,        // first sequence in this root
        long toSequence,          // last sequence in this root
        String previousRoot,      // hex-encoded previous root (null for genesis)
        Instant generatedAt,      // UTC instant
        String signerKeyId,       // who signed this root
        String signature,         // cryptographic signature over root
        String checkpointId       // unique checkpoint identifier
    ) {
        public AuditRoot {
            if (rootVersion < 1) throw new IllegalArgumentException("rootVersion must be >= 1");
            if (hashAlgorithm == null || hashAlgorithm.isBlank()) throw new IllegalArgumentException("hashAlgorithm required");
            if (merkleRoot == null || merkleRoot.isBlank()) throw new IllegalArgumentException("merkleRoot required");
            if (eventCount < 0) throw new IllegalArgumentException("eventCount must be >= 0");
            if (fromSequence < 0) throw new IllegalArgumentException("fromSequence must be >= 0");
            if (toSequence < fromSequence) throw new IllegalArgumentException("toSequence must be >= fromSequence");
            if (generatedAt == null) throw new IllegalArgumentException("generatedAt required");
            if (signerKeyId == null || signerKeyId.isBlank()) throw new IllegalArgumentException("signerKeyId required");
            if (signature == null || signature.isBlank()) throw new IllegalArgumentException("signature required");
            if (checkpointId == null || checkpointId.isBlank()) throw new IllegalArgumentException("checkpointId required");
        }
    }

    record InclusionProof(
        long sequenceNumber,
        String merkleRoot,
        String[] proofPath,     // sibling hashes from leaf to root
        boolean verified
    ) {
        public InclusionProof {
            if (sequenceNumber < 0) throw new IllegalArgumentException("sequenceNumber must be >= 0");
            if (merkleRoot == null || merkleRoot.isBlank()) throw new IllegalArgumentException("merkleRoot required");
            if (proofPath == null || proofPath.length == 0) throw new IllegalArgumentException("proofPath required");
        }
    }

    record ConsistencyProof(
        String fromRoot,
        long fromTreeSize,
        String toRoot,
        long toTreeSize,
        String[] proofPath,
        boolean verified
    ) {
        public ConsistencyProof {
            if (fromRoot == null || fromRoot.isBlank()) throw new IllegalArgumentException("fromRoot required");
            if (toRoot == null || toRoot.isBlank()) throw new IllegalArgumentException("toRoot required");
            if (proofPath == null || proofPath.length == 0) throw new IllegalArgumentException("proofPath required");
        }
    }

    // --- Deprecated: old root/records kept for backward compatibility ---

    /**
     * @deprecated Use {@link #currentRoot()} instead.
     */
    @Deprecated
    default AuditRootLegacy root() {
        throw new UnsupportedOperationException("Use currentRoot() returning AuditRoot instead.");
    }

    /**
     * @deprecated Replaced by {@link AuditRoot} with cryptographic signature and checkpoint metadata.
     */
    @Deprecated(forRemoval = true)
    record AuditRootLegacy(
        String merkleRoot,
        long eventCount,
        Long fromSequence,
        Long toSequence,
        LocalDateTime generatedAt
    ) {
        // kept for backward compatibility only
    }
}
