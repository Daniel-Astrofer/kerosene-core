package source.ledger.sync;

import java.util.List;

public record QuorumTopology(List<QuorumPeer> remotePeers) {

    private static final int LOCAL_NODE_COUNT = 1;

    public QuorumTopology {
        remotePeers = List.copyOf(remotePeers);
    }

    public int localNodeCount() {
        return LOCAL_NODE_COUNT;
    }

    public int totalNodes() {
        return LOCAL_NODE_COUNT + remotePeers.size();
    }

    public int requiredQuorum() {
        return (totalNodes() / 2) + 1;
    }
}
