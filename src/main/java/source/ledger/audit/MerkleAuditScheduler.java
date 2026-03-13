package source.ledger.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers a Merkle root snapshot of all internal ledger balances.
 *
 * The interval is configured via {@code audit.merkle.interval-ms} (default 5
 * min).
 * Each execution persists a new {@link MerkleAuditEntity} checkpoint.
 *
 * Future enhancement: after persisting, optionally broadcast the root as a
 * Bitcoin OP_RETURN transaction to create an immutable public timestamp.
 */
@Component
public class MerkleAuditScheduler {

    private static final Logger log = LoggerFactory.getLogger(MerkleAuditScheduler.class);

    private final MerkleAuditService auditService;

    public MerkleAuditScheduler(MerkleAuditService auditService) {
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${audit.merkle.interval-ms:300000}", initialDelayString = "${audit.merkle.initial-delay-ms:60000}")
    public void runAudit() {
        try {
            MerkleAuditEntity checkpoint = auditService.computeAndPersist();
            log.info("[MerkleAudit] Checkpoint persisted — root={} ledgers={}",
                    checkpoint.getMerkleRoot(), checkpoint.getLedgerCount());
        } catch (Exception ex) {
            log.error("[MerkleAudit] Audit failed: {}", ex.getMessage(), ex);
        }
    }
}
