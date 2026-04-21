package source.transactions.application.transaction.monitoring;

public interface PendingTransactionMonitorHandlerChain {

    void next(PendingTransactionMonitorContext context);
}
