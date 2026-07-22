package source.sovereign.quorum;

public interface QuorumTransport {

    QuorumPhaseResult prepare(String transactionHash, QuorumTopology topology);

    QuorumPhaseResult commit(String transactionHash, QuorumTopology topology);

    QuorumPhaseResult healthCheck(QuorumTopology topology);
}
