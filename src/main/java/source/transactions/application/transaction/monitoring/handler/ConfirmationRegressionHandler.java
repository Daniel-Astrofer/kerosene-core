package source.transactions.application.transaction.monitoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorContext;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandler;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandlerChain;
import source.transactions.application.transaction.monitoring.PendingTransactionStatusMatcher;

@Component
@Order(50)
public class ConfirmationRegressionHandler implements PendingTransactionMonitorHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationRegressionHandler.class);

    private final int minimumConfirmations;

    public ConfirmationRegressionHandler(@Value("${bitcoin.min-confirmations:3}") int minimumConfirmations) {
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
    }

    @Override
    public void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain) {
        if (PendingTransactionStatusMatcher.matches(context.getTransaction().getStatus(), "CONFIRMED")
                && context.getConfirmations() < minimumConfirmations) {
            context.getTransaction().setStatus("AUTO_RESOLUTION_PENDING");
            context.getTransaction().setConfirmedAt(null);
            context.getTransaction().setErrorMessage(
                    "Confirmation depth dropped below " + minimumConfirmations
                            + "; possible reorg after settlement. Funds stay protected while automatic reconciliation runs.");
            log.warn("Transaction {} moved to AUTO_RESOLUTION_PENDING after confirmation depth dropped to {}.",
                    context.getTransaction().getTxid(),
                    context.getConfirmations());
            context.stop();
            return;
        }

        chain.next(context);
    }
}
