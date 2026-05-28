package source.transactions.application.transaction.monitoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorContext;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandler;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandlerChain;
import source.transactions.application.transaction.monitoring.PendingTransactionObservationPort;

@Component
@Order(40)
public class SynchronizeTransactionConfirmationsHandler implements PendingTransactionMonitorHandler {

    private static final Logger log = LoggerFactory.getLogger(SynchronizeTransactionConfirmationsHandler.class);

    private final PendingTransactionObservationPort pendingTransactionObservationPort;

    public SynchronizeTransactionConfirmationsHandler(PendingTransactionObservationPort pendingTransactionObservationPort) {
        this.pendingTransactionObservationPort = pendingTransactionObservationPort;
    }

    @Override
    public void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain) {
        int confirmations = context.getConfirmations();
        context.getTransaction().setConfirmations(confirmations);

        try {
            pendingTransactionObservationPort.syncConfirmations(context.getTransaction().getTxid(), confirmations);
        } catch (RuntimeException ex) {
            log.error("Failed to update history confirmations: {}", ex.getMessage());
        }

        log.info("Transaction {} has {} confirmation(s)", context.getTransaction().getTxid(), confirmations);
        chain.next(context);
    }
}
