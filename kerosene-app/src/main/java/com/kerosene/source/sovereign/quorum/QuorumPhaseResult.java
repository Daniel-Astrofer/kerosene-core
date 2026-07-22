package source.sovereign.quorum;

import java.util.List;

public record QuorumPhaseResult(
        QuorumPhase phase,
        int acceptedNodes,
        int totalNodes,
        int requiredQuorum,
        boolean timedOut,
        List<QuorumPeerResult> peerResults) {

    public QuorumPhaseResult {
        peerResults = List.copyOf(peerResults);
    }

    public boolean reachedQuorum() {
        return acceptedNodes >= requiredQuorum;
    }

    public String summary() {
        return acceptedNodes + "/" + totalNodes + " ACKs (need " + requiredQuorum + ")";
    }
}
