package source.transactions.application.transaction.monitoring;

import source.transactions.model.PendingTransaction;

public interface PendingTransactionObservationPort {

    void notifyPendingDepositDetected(PendingTransaction transaction);

    void syncConfirmations(String txid, int confirmations);
}
