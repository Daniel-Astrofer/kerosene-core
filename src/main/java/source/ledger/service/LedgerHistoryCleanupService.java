package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.time.LocalDateTime;

@Service
public class LedgerHistoryCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(LedgerHistoryCleanupService.class);

    private final LedgerTransactionHistoryRepository repository;
    private final long retentionHours;

    public LedgerHistoryCleanupService(
            LedgerTransactionHistoryRepository repository,
            @Value("${ledger.history.retention.hours:24}") long retentionHours) {
        this.repository = repository;
        this.retentionHours = Math.max(1, retentionHours);
    }

    /**
     * Executes every five minutes to delete readable operational transaction history
     * older than the configured ephemeral retention window.
     *
     * This cleanup does not disable ledger integrity. Durable user-facing history
     * belongs on the mobile device; backend audit continuity is kept through
     * hashes, commitments, Merkle roots, and minimum settlement state.
     */
    @Scheduled(cron = "0 */5 * * * *") // Every five minutes
    @Transactional
    public void cleanupOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);
        logger.info("Starting cleanup of ephemeral ledger transaction history older than {}", cutoff);

        int deletedCount = repository.deleteByCreatedAtBefore(cutoff);

        logger.info("Successfully deleted {} expired ephemeral ledger transaction history records.", deletedCount);
    }
}
