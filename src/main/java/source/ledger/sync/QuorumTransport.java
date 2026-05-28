package source.ledger.sync;

public interface QuorumTransport {

    QuorumPhaseResult prepare(String transactionHash, QuorumTopology topology);

    QuorumPhaseResult commit(String transactionHash, QuorumTopology topology);

    QuorumPhaseResult healthCheck(QuorumTopology topology);
}
