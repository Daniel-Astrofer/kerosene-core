package source.transactions.application.transaction.monitoring;

public interface PendingTransactionMonitorHandler {

    void handle(PendingTransactionMonitorContext context, PendingTransactionMonitorHandlerChain chain);
}
