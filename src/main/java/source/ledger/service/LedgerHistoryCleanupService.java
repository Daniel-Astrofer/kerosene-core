package source.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.time.LocalDateTime;

@Service
public class LedgerHistoryCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(LedgerHistoryCleanupService.class);

    private final LedgerTransactionHistoryRepository repository;

    public LedgerHistoryCleanupService(LedgerTransactionHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes every hour to delete transaction history older than 24 hours.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupOldHistory() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        logger.info("Starting cleanup of ledger transaction history older than {}", twentyFourHoursAgo);

        int deletedCount = repository.deleteByCreatedAtBefore(twentyFourHoursAgo);

        logger.info("Successfully deleted {} old ledger transaction history records.", deletedCount);
    }
}
