package source.transactions.application.transaction.monitoring;

import source.transactions.model.PendingTransaction;

public class PendingTransactionMonitorContext {

    private final PendingTransaction transaction;
    private PendingTransactionBlockchainPort.BlockchainTransactionSnapshot blockchainTransactionSnapshot;
    private boolean stopChain;
    private boolean persistState = true;

    public PendingTransactionMonitorContext(PendingTransaction transaction) {
        this.transaction = transaction;
    }

    public PendingTransaction getTransaction() {
        return transaction;
    }

    public PendingTransactionBlockchainPort.BlockchainTransactionSnapshot getBlockchainTransactionSnapshot() {
        return blockchainTransactionSnapshot;
    }

    public void setBlockchainTransactionSnapshot(
            PendingTransactionBlockchainPort.BlockchainTransactionSnapshot blockchainTransactionSnapshot) {
        this.blockchainTransactionSnapshot = blockchainTransactionSnapshot;
    }

    public int getConfirmations() {
        return blockchainTransactionSnapshot != null ? blockchainTransactionSnapshot.confirmations() : 0;
    }

    public void stop() {
        this.stopChain = true;
    }

    public boolean shouldStop() {
        return stopChain;
    }

    public boolean shouldPersistState() {
        return persistState;
    }

    public void skipPersistence() {
        this.persistState = false;
    }
}
