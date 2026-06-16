package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.common.infra.RedisAvailabilityGuard;
import source.transactions.application.transaction.TransactionPendingPort;
import source.transactions.application.transaction.monitoring.MonitorPendingTransactionUseCase;
import source.transactions.application.transaction.monitoring.TransactionMonitoringRateLimitException;
import source.transactions.model.PendingTransaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class PendingTransactionMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionMonitoringScheduler.class);

    @Value("${blockchain.monitor.interval.min:10000}")
    private long minInterval;

    @Value("${blockchain.monitor.interval.max:120000}")
    private long maxInterval;

    private final AtomicLong currentBackoffMs = new AtomicLong();

    private final TransactionPendingPort transactionPendingPort;
    private final MonitorPendingTransactionUseCase monitorPendingTransactionUseCase;
    private final RedisAvailabilityGuard redisAvailabilityGuard;

    public PendingTransactionMonitoringScheduler(
            TransactionPendingPort transactionPendingPort,
            MonitorPendingTransactionUseCase monitorPendingTransactionUseCase,
            RedisAvailabilityGuard redisAvailabilityGuard) {
        this.transactionPendingPort = transactionPendingPort;
        this.monitorPendingTransactionUseCase = monitorPendingTransactionUseCase;
        this.redisAvailabilityGuard = redisAvailabilityGuard;
    }

    @Scheduled(fixedDelay = 30000)
    public void monitorPendingTransactions() {
        if (!redisAvailabilityGuard.isAvailable()) {
            log.debug("[BlockchainMonitor] Skipping cycle because Redis is unavailable: {}",
                    redisAvailabilityGuard.describeLastFailure());
            return;
        }

        if (currentBackoffMs.get() > 0) {
            long remainingBackoffMs = currentBackoffMs.updateAndGet(backoffMs -> Math.max(0, backoffMs - 30000));
            log.debug("[BlockchainMonitor] Skipping cycle (backoff cooling down: {}ms remaining)", remainingBackoffMs);
            return;
        }

        List<PendingTransaction> pendingTransactions = transactionPendingPort.findPendingTransactions();
        if (pendingTransactions.isEmpty()) {
            return;
        }

        log.info("Checking {} pending transaction(s)", pendingTransactions.size());
        boolean rateLimitHit = false;
        for (PendingTransaction transaction : pendingTransactions) {
            try {
                monitorPendingTransactionUseCase.check(transaction);
            } catch (TransactionMonitoringRateLimitException ex) {
                log.warn("[BlockchainMonitor] Rate limited by blockchain API. Backing off.");
                currentBackoffMs.updateAndGet(backoffMs ->
                        Math.min(backoffMs == 0 ? minInterval : backoffMs * 2, maxInterval));
                rateLimitHit = true;
                break;
            } catch (Exception ex) {
                log.error("Error checking transaction {}: {}", transaction.getTxid(), ex.getMessage());
            }
        }

        if (!rateLimitHit && currentBackoffMs.get() > 0) {
            currentBackoffMs.updateAndGet(backoffMs -> Math.max(0, backoffMs / 2));
        }
    }
}
