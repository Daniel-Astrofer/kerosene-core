package source.transactions.application.transaction.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.transactions.model.PendingTransaction;
import source.transactions.application.transaction.TransactionPendingPort;

import java.time.LocalDateTime;

@Service
public class MonitorPendingTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(MonitorPendingTransactionUseCase.class);

    private final PendingTransactionMonitorPipeline pendingTransactionMonitorPipeline;
    private final TransactionPendingPort transactionPendingPort;

    public MonitorPendingTransactionUseCase(
            PendingTransactionMonitorPipeline pendingTransactionMonitorPipeline,
            TransactionPendingPort transactionPendingPort) {
        this.pendingTransactionMonitorPipeline = pendingTransactionMonitorPipeline;
        this.transactionPendingPort = transactionPendingPort;
    }

    public void check(PendingTransaction transaction) {
        PendingTransactionMonitorContext context = new PendingTransactionMonitorContext(transaction);

        try {
            log.info("Checking transaction: {}", transaction.getTxid());
            pendingTransactionMonitorPipeline.execute(context);
        } catch (TransactionMonitoringRateLimitException ex) {
            throw ex;
        } catch (Exception ex) {
            handleUnexpectedFailure(transaction, ex);
            return;
        }

        if (context.shouldPersistState()) {
            transactionPendingPort.save(transaction);
        }
    }

    private void handleUnexpectedFailure(PendingTransaction transaction, Exception ex) {
        log.error("Error checking transaction {}: {}", transaction.getTxid(), ex.getMessage());

        if (transaction.getCreatedAt() != null
                && transaction.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            transaction.setStatus("FAILED");
            transaction.setErrorMessage("Transaction not found after 24 hours");
            transactionPendingPort.save(transaction);
        }
    }
}
