package source.ledger.sync;

public record QuorumPeerResult(
        QuorumPeer peer,
        boolean accepted,
        boolean timedOut,
        int statusCode,
        String error) {

    public static QuorumPeerResult accepted(QuorumPeer peer, int statusCode) {
        return new QuorumPeerResult(peer, true, false, statusCode, null);
    }

    public static QuorumPeerResult rejected(QuorumPeer peer, int statusCode) {
        return new QuorumPeerResult(peer, false, false, statusCode, null);
    }

    public static QuorumPeerResult failed(QuorumPeer peer, String error) {
        return new QuorumPeerResult(peer, false, false, 0, error);
    }

    public static QuorumPeerResult timeout(QuorumPeer peer) {
        return new QuorumPeerResult(peer, false, true, 0, "timeout");
    }
}
