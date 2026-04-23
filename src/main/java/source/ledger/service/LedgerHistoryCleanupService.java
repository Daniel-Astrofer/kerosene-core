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
    private final long retentionDays;

    public LedgerHistoryCleanupService(
            LedgerTransactionHistoryRepository repository,
            @Value("${ledger.history.retention.days:90}") long retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    /**
     * Executes every hour to delete transaction history older than the configured
     * retention window.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        logger.info("Starting cleanup of ledger transaction history older than {}", cutoff);

        int deletedCount = repository.deleteByCreatedAtBefore(cutoff);

        logger.info("Successfully deleted {} old ledger transaction history records.", deletedCount);
    }
}
