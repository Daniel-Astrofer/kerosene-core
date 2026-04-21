package source.transactions.application.transaction.monitoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorContext;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandler;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandlerChain;

@Component
@Order(10)
public class InvalidPendingTransactionHandler implements PendingTransactionMonitorHandler {

    private static final Logger log = LoggerFactory.getLogger(InvalidPendingTransactionHandler.class);

    @Override
    public void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain) {
        String txid = context.getTransaction().getTxid();
        if (txid == null || txid.startsWith("mock-") || txid.length() != 64) {
            log.warn("Marking INVALID transaction as FAILED: {}", txid);
            context.getTransaction().setStatus("FAILED");
            context.getTransaction().setErrorMessage("Invalid TXID format - Cleanup");
            context.stop();
            return;
        }

        chain.next(context);
    }
}
