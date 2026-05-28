package source.transactions.application.transaction.monitoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorContext;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandler;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandlerChain;
import source.transactions.application.transaction.monitoring.PendingTransactionSettlementPort;
import source.transactions.application.transaction.monitoring.PendingTransactionStatusMatcher;

import java.time.LocalDateTime;

@Component
@Order(60)
public class ConfirmedTransactionSettlementHandler implements PendingTransactionMonitorHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmedTransactionSettlementHandler.class);

    private final PendingTransactionSettlementPort pendingTransactionSettlementPort;
    private final int minimumConfirmations;

    public ConfirmedTransactionSettlementHandler(
            PendingTransactionSettlementPort pendingTransactionSettlementPort,
            @Value("${bitcoin.min-confirmations:3}") int minimumConfirmations) {
        this.pendingTransactionSettlementPort = pendingTransactionSettlementPort;
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
    }

    @Override
    public void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain) {
        if (context.getConfirmations() >= minimumConfirmations
                && !PendingTransactionStatusMatcher.matches(context.getTransaction().getStatus(), "CONFIRMED")) {
            context.getTransaction().setStatus("CONFIRMED");
            context.getTransaction().setConfirmedAt(LocalDateTime.now());
            log.info("Transaction {} CONFIRMED after {} confirmations",
                    context.getTransaction().getTxid(),
                    context.getConfirmations());

            try {
                pendingTransactionSettlementPort.settleConfirmedTransaction(
                        context.getTransaction(),
                        context.getConfirmations());
            } catch (RuntimeException ex) {
                log.error("Failed to process idempotently tx {}: {}",
                        context.getTransaction().getTxid(),
                        ex.getMessage());
                context.skipPersistence();
                context.stop();
                return;
            }
        }

        chain.next(context);
    }
}
