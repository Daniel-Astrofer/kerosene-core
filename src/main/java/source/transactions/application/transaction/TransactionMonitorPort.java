package source.transactions.application.transaction;

import source.transactions.model.PendingTransaction;

public interface TransactionMonitorPort {

    void checkTransaction(PendingTransaction transaction);
}
