package source.ledger.infra.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.ledger.application.balance.LedgerBalanceConsensusPort;
import source.ledger.exceptions.LedgerExceptions;
import source.sovereign.quorum.QuorumSyncService;

@Component
public class QuorumLedgerBalanceConsensusAdapter implements LedgerBalanceConsensusPort {

    private static final Logger log = LoggerFactory.getLogger(QuorumLedgerBalanceConsensusAdapter.class);

    private final QuorumSyncService quorumSyncService;

    public QuorumLedgerBalanceConsensusAdapter(QuorumSyncService quorumSyncService) {
        this.quorumSyncService = quorumSyncService;
    }

    @Override
    public void requireConsensus(String ledgerHash) {
        boolean quorumAccepted;
        try {
            quorumAccepted = quorumSyncService.proposeTransactionToQuorum(ledgerHash);
        } catch (Exception exception) {
            log.error("[LedgerBalanceConsensus] Quorum proposal failed for hash {}: {}",
                    ledgerHash,
                    exception.getMessage());
            throw new LedgerExceptions.LedgerSyncException(
                    "Quorum exception — transaction aborted: " + exception.getMessage());
        }

        if (!quorumAccepted) {
            throw new LedgerExceptions.LedgerSyncException(
                    "Failed to reach consensus quorum across shards for this balance operation.");
        }
    }
}
