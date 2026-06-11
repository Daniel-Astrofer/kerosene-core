package source.kfe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.sovereign.quorum.FailStopPolicy;
import source.sovereign.quorum.QuorumMembership;
import source.sovereign.quorum.QuorumPeer;
import source.sovereign.quorum.QuorumPhaseResult;
import source.sovereign.quorum.QuorumTopology;
import source.sovereign.quorum.QuorumTransport;

import java.util.List;

@Service
public class KfeQuorumGateway {

    private final QuorumTransport transport;
    private final QuorumMembership membership;
    private final FailStopPolicy failStopPolicy;
    private final int minimumHealthyNodes;

    public KfeQuorumGateway(
            QuorumTransport transport,
            QuorumMembership membership,
            FailStopPolicy failStopPolicy,
            @Value("${kfe.quorum.minimum-healthy-nodes:2}") int minimumHealthyNodes) {
        this.transport = transport;
        this.membership = membership;
        this.failStopPolicy = failStopPolicy;
        this.minimumHealthyNodes = Math.max(2, minimumHealthyNodes);
    }

    public Result requireHealthyUnanimousConsensus(String proposalHash) {
        failStopPolicy.assertWritesAllowed(proposalHash);

        QuorumTopology configuredTopology = membership.current();
        QuorumPhaseResult health = transport.healthCheck(configuredTopology);
        List<QuorumPeer> healthyPeers = health.peerResults().stream()
                .filter(result -> result.accepted() && !result.timedOut())
                .map(result -> result.peer())
                .toList();
        QuorumTopology healthyTopology = new QuorumTopology(healthyPeers);

        if (healthyTopology.totalNodes() < minimumHealthyNodes) {
            failStopPolicy.recordQuorumFailure("KFE quorum has fewer than "
                    + minimumHealthyNodes + " healthy attested nodes.");
            throw new IllegalStateException("KFE quorum fail-stop: fewer than "
                    + minimumHealthyNodes + " healthy attested nodes.");
        }

        QuorumPhaseResult prepare = transport.prepare(proposalHash, healthyTopology);
        requireUnanimous(prepare, "PREPARE", proposalHash);

        QuorumPhaseResult commit = transport.commit(proposalHash, healthyTopology);
        requireUnanimous(commit, "COMMIT", proposalHash);

        failStopPolicy.recordQuorumSuccess();
        return new Result(commit.acceptedNodes(), healthyTopology.totalNodes());
    }

    private void requireUnanimous(QuorumPhaseResult result, String phase, String proposalHash) {
        if (result.timedOut() || result.acceptedNodes() != result.totalNodes()) {
            failStopPolicy.recordQuorumFailure("KFE " + phase + " unanimity failed for " + proposalHash
                    + ": " + result.summary());
            throw new IllegalStateException("KFE quorum " + phase
                    + " failed: expected all healthy nodes, got " + result.summary() + ".");
        }
    }

    public record Result(int acceptedNodes, int totalHealthyNodes) {
    }
}
