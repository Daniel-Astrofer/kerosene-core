package com.kerosene.common.financial;

import java.time.Instant;
import java.util.Set;

/**
 * Constitutional threshold quorum — NOT "healthy unanimity".
 * Every decision is cryptographically attributable to specific members.
 */
public interface FinancialQuorumPort {

    /**
     * Request threshold-based quorum consensus.
     * @return QuorumDecision with full attribution and proof
     */
    default QuorumDecision requireThresholdConsensus(Proposal proposal) {
        throw new UnsupportedOperationException("Constitutional threshold quorum is not implemented by this adapter");
    }

    /**
     * A proposal submitted for quorum voting.
     */
    record Proposal(
        String proposalHash,
        String constitutionHash,
        long constitutionEpoch,
        Instant submittedAt,
        Instant expiresAt
    ) {
        public Proposal {
            if (proposalHash == null || proposalHash.isBlank()) throw new IllegalArgumentException("proposalHash required");
            if (constitutionHash == null || constitutionHash.isBlank()) throw new IllegalArgumentException("constitutionHash required");
            if (constitutionEpoch < 0) throw new IllegalArgumentException("constitutionEpoch must be >= 0");
            if (submittedAt == null) throw new IllegalArgumentException("submittedAt required");
            if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
            if (!expiresAt.isAfter(submittedAt)) throw new IllegalArgumentException("expiresAt must be after submittedAt");
        }
    }

    enum Decision {
        ACCEPTED,
        REJECTED,
        TIMED_OUT
    }

    /**
     * Fully-attributed quorum decision with all verification fields.
     * State invariants are enforced by the compact constructor.
     */
    record QuorumDecision(
        Decision decision,
        String proposalHash,
        String constitutionHash,
        long constitutionEpoch,
        int configuredMembers,
        int requiredThreshold,
        Set<String> acceptedMembers,
        Set<String> rejectedMembers,
        Set<String> unavailableMembers,
        String aggregateProof,
        Instant decidedAt
    ) {
        public QuorumDecision {
            if (proposalHash == null || proposalHash.isBlank()) throw new IllegalArgumentException("proposalHash required");
            if (constitutionHash == null || constitutionHash.isBlank()) throw new IllegalArgumentException("constitutionHash required");
            if (decision == null) throw new IllegalArgumentException("decision required");

            if (configuredMembers < 1) throw new IllegalArgumentException("configuredMembers must be >= 1");
            if (requiredThreshold < 1) throw new IllegalArgumentException("requiredThreshold must be >= 1");
            if (requiredThreshold > configuredMembers) throw new IllegalArgumentException("requiredThreshold cannot exceed configuredMembers");
            if (constitutionEpoch < 0) throw new IllegalArgumentException("constitutionEpoch must be >= 0");

            if (acceptedMembers == null) acceptedMembers = Set.of();
            if (rejectedMembers == null) rejectedMembers = Set.of();
            if (unavailableMembers == null) unavailableMembers = Set.of();

            for (String member : acceptedMembers) {
                if (rejectedMembers.contains(member)) throw new IllegalArgumentException("member " + member + " in both accepted and rejected");
                if (unavailableMembers.contains(member)) throw new IllegalArgumentException("member " + member + " in both accepted and unavailable");
            }
            for (String member : rejectedMembers) {
                if (unavailableMembers.contains(member)) throw new IllegalArgumentException("member " + member + " in both rejected and unavailable");
            }

            int totalAttributed = acceptedMembers.size() + rejectedMembers.size() + unavailableMembers.size();
            if (totalAttributed != configuredMembers) throw new IllegalArgumentException(
                "attributed members (" + totalAttributed + ") != configuredMembers (" + configuredMembers + ")"
            );

            switch (decision) {
                case ACCEPTED -> {
                    if (acceptedMembers.size() < requiredThreshold)
                        throw new IllegalArgumentException("ACCEPTED requires acceptedMembers >= " + requiredThreshold + ", got " + acceptedMembers.size());
                    if (aggregateProof == null || aggregateProof.isBlank())
                        throw new IllegalArgumentException("aggregateProof required for ACCEPTED");
                }
                case REJECTED -> {
                    if (rejectedMembers.isEmpty())
                        throw new IllegalArgumentException("REJECTED requires at least one rejectedMember");
                }
                case TIMED_OUT -> {
                    if (unavailableMembers.isEmpty())
                        throw new IllegalArgumentException("TIMED_OUT requires at least one unavailableMember");
                }
            }

            if (decidedAt == null) throw new IllegalArgumentException("decidedAt required");
        }
    }

    @Deprecated(forRemoval = true)
    Result requireHealthyUnanimousConsensus(String proposalHash);

    @Deprecated(forRemoval = true)
    record Result(int acceptedNodes, int totalHealthyNodes) {
    }
}
