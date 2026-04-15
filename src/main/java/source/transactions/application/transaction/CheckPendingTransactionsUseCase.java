package source.transactions.application.transaction;

import org.springframework.stereotype.Service;
import source.transactions.model.PendingTransaction;

@Service
public class CheckPendingTransactionsUseCase {

    private final TransactionPendingPort transactionPendingPort;
    private final TransactionMonitorPort transactionMonitorPort;

    public CheckPendingTransactionsUseCase(
            TransactionPendingPort transactionPendingPort,
            TransactionMonitorPort transactionMonitorPort) {
        this.transactionPendingPort = transactionPendingPort;
        this.transactionMonitorPort = transactionMonitorPort;
    }

    public void checkAll() {
        for (PendingTransaction transaction : transactionPendingPort.findPendingTransactions()) {
            transactionMonitorPort.checkTransaction(transaction);
        }
    }
}
