package source.transactions.application.transaction.monitoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionBlockchainPort;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorContext;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandler;
import source.transactions.application.transaction.monitoring.PendingTransactionMonitorHandlerChain;

@Component
@Order(20)
public class LoadBlockchainSnapshotHandler implements PendingTransactionMonitorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoadBlockchainSnapshotHandler.class);

    private final PendingTransactionBlockchainPort pendingTransactionBlockchainPort;

    public LoadBlockchainSnapshotHandler(PendingTransactionBlockchainPort pendingTransactionBlockchainPort) {
        this.pendingTransactionBlockchainPort = pendingTransactionBlockchainPort;
    }

    @Override
    public void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain) {
        pendingTransactionBlockchainPort.loadTransaction(context.getTransaction().getTxid())
                .ifPresentOrElse(snapshot -> {
                    context.setBlockchainTransactionSnapshot(snapshot);
                    chain.next(context);
                }, () -> {
                    log.warn("Transaction {} not found on blockchain yet", context.getTransaction().getTxid());
                    context.skipPersistence();
                    context.stop();
                });
    }
}
