package source.transactions.application.transaction;

import source.transactions.model.PendingTransaction;

import java.util.List;
import java.util.Optional;

public interface TransactionPendingPort {

    PendingTransaction save(PendingTransaction transaction);

    Optional<PendingTransaction> findByTxid(String txid);

    List<PendingTransaction> findPendingTransactions();
}
