package source.transactions.application.transaction;

import org.springframework.stereotype.Service;
import source.transactions.application.transaction.monitoring.MonitorPendingTransactionUseCase;
import source.transactions.model.PendingTransaction;

@Service
public class CheckPendingTransactionsUseCase {

    private final TransactionPendingPort transactionPendingPort;
    private final MonitorPendingTransactionUseCase monitorPendingTransactionUseCase;

    public CheckPendingTransactionsUseCase(
            TransactionPendingPort transactionPendingPort,
            MonitorPendingTransactionUseCase monitorPendingTransactionUseCase) {
        this.transactionPendingPort = transactionPendingPort;
        this.monitorPendingTransactionUseCase = monitorPendingTransactionUseCase;
    }

    public void checkAll() {
        for (PendingTransaction transaction : transactionPendingPort.findPendingTransactions()) {
            monitorPendingTransactionUseCase.check(transaction);
        }
    }
}
