package source.transactions.application.transaction.monitoring;

import java.util.Optional;

public interface PendingTransactionBlockchainPort {

    Optional<BlockchainTransactionSnapshot> loadTransaction(String txid);

    record BlockchainTransactionSnapshot(int confirmations) {
    }
}
