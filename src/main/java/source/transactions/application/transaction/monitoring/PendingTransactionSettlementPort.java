package source.transactions.application.transaction.monitoring;

import source.transactions.model.PendingTransaction;

public interface PendingTransactionSettlementPort {

    void settleConfirmedTransaction(PendingTransaction transaction, int confirmations);
}
