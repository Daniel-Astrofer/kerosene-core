package source.sovereign.quorum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates quorum decisions for ledger writes.
 *
 * Transport, membership discovery, and fail-stop lifecycle are delegated to
 * dedicated ports so this service only owns the 2PC business decision.
 */
@Service
public class QuorumSyncService {

    private static final Logger logger = LoggerFactory.getLogger(QuorumSyncService.class);

    private final QuorumTransport transport;
    private final QuorumMembership membership;
    private final FailStopPolicy failStopPolicy;
    private final AtomicLong totalTransactionsProposed = new AtomicLong(0);
    private final AtomicLong totalTransactionsAccepted = new AtomicLong(0);

    public QuorumSyncService(
            QuorumTransport transport,
            QuorumMembership membership,
            FailStopPolicy failStopPolicy) {
        this.transport = transport;
        this.membership = membership;
        this.failStopPolicy = failStopPolicy;

        QuorumTopology topology = membership.current();
        logger.info("[Quorum Sync] Initialized. Requiring {}/{} nodes.",
                topology.requiredQuorum(), topology.totalNodes());
    }

    public boolean isFailStopMode() {
        return failStopPolicy.isFailStopMode();
    }

    public Instant getLastQuorumSuccess() {
        return failStopPolicy.lastQuorumSuccess();
    }

    public long getTotalProposed() {
        return totalTransactionsProposed.get();
    }

    public long getTotalAccepted() {
        return totalTransactionsAccepted.get();
    }

    public int getRequiredQuorum() {
        return membership.current().requiredQuorum();
    }

    public int getTotalNodes() {
        return membership.current().totalNodes();
    }

    /**
     * Executes PREPARE + COMMIT against the current quorum topology.
     *
     * @param transactionHash SHA-256 hash of the proposed transaction.
     * @return true when the transaction reached commit quorum.
     * @throws SplitBrainException when fail-stop mode is active.
     */
    public boolean proposeTransactionToQuorum(String transactionHash) {
        totalTransactionsProposed.incrementAndGet();
        failStopPolicy.assertWritesAllowed(transactionHash);

        QuorumTopology topology = membership.current();
        logger.debug("[Quorum Sync] Phase 1 PREPARE: proposing {} to {} nodes.",
                transactionHash, topology.totalNodes());

        QuorumPhaseResult prepare = transport.prepare(transactionHash, topology);
        if (prepare.timedOut()) {
            logger.error("[Quorum Sync] Phase 1 timed out. {}.", prepare.summary());
            failStopPolicy.enterFailStop("Phase 1 quorum timeout while preparing " + transactionHash);
            return false;
        }

        if (!prepare.reachedQuorum()) {
            logger.error("[Quorum Sync] Phase 1 failed. {}.", prepare.summary());
            failStopPolicy.recordQuorumFailure("Phase 1 quorum failure for " + transactionHash + ": "
                    + prepare.summary());
            return false;
        }

        logger.debug("[Quorum Sync] Phase 2 COMMIT: broadcasting {} after {}.",
                transactionHash, prepare.summary());

        QuorumPhaseResult commit = transport.commit(transactionHash, topology);
        if (commit.timedOut()) {
            logger.error("[Quorum Sync] Phase 2 timed out. {}.", commit.summary());
            failStopPolicy.enterFailStop("Phase 2 commit timeout while committing " + transactionHash);
            return false;
        }

        if (!commit.reachedQuorum()) {
            logger.error("[Quorum Sync] Phase 2 failed. {}.", commit.summary());
            failStopPolicy.enterFailStop("Phase 2 insufficient commits for " + transactionHash + ": "
                    + commit.summary());
            return false;
        }

        failStopPolicy.recordQuorumSuccess();
        totalTransactionsAccepted.incrementAndGet();
        logger.info("[Quorum Sync] Commit success. Transaction {} accepted. {}.",
                transactionHash, commit.summary());
        return true;
    }

    public QuorumHealth checkQuorumHealth() {
        QuorumTopology topology = membership.current();
        QuorumPhaseResult health = transport.healthCheck(topology);
        return new QuorumHealth(
                health.acceptedNodes(),
                topology.requiredQuorum(),
                topology.totalNodes(),
                health.reachedQuorum() && !health.timedOut(),
                health.timedOut());
    }

    /**
     * Operator entry point after the network has been reconnected manually.
     * Writes are released only after the configured topology reaches quorum.
     */
    public void exitFailStopMode() {
        QuorumHealth health = checkQuorumHealth();
        if (health.quorumAvailable()) {
            failStopPolicy.clearFailStop();
            logger.info("[Quorum Sync] Fail-stop mode cleared. Quorum restored ({}/{} nodes live).",
                    health.activeNodes(), health.totalNodes());
            return;
        }

        logger.error("[Quorum Sync] Cannot exit fail-stop: quorum still unavailable ({}/{} nodes live, need {}).",
                health.activeNodes(), health.totalNodes(), health.requiredNodes());
        throw new SplitBrainException("Cannot exit Fail-Stop: quorum not restored. "
                + health.activeNodes() + "/" + health.totalNodes() + " nodes.");
    }

    public record QuorumHealth(
            int activeNodes,
            int requiredNodes,
            int totalNodes,
            boolean quorumAvailable,
            boolean timedOut) {
    }
}
