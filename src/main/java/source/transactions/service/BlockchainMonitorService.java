package source.transactions.service;

import org.springframework.stereotype.Service;
import source.transactions.application.transaction.TransactionPendingPort;
import source.transactions.application.transaction.monitoring.MonitorPendingTransactionUseCase;
import source.transactions.model.PendingTransaction;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BlockchainMonitorService {

    private final TransactionPendingPort transactionPendingPort;
    private final MonitorPendingTransactionUseCase monitorPendingTransactionUseCase;

    public BlockchainMonitorService(
            TransactionPendingPort transactionPendingPort,
            MonitorPendingTransactionUseCase monitorPendingTransactionUseCase) {
        this.transactionPendingPort = transactionPendingPort;
        this.monitorPendingTransactionUseCase = monitorPendingTransactionUseCase;
    }

    public void checkTransaction(PendingTransaction transaction) {
        monitorPendingTransactionUseCase.check(transaction);
    }

    public PendingTransaction registerTransaction(
            String txid,
            String fromAddress,
            String toAddress,
            Long userId,
            BigDecimal amount,
            Long fee) {
        PendingTransaction entity = new PendingTransaction(txid, fromAddress, toAddress, amount, fee, userId);
        return transactionPendingPort.save(entity);
    }

    public PendingTransaction getTransaction(String txid) {
        return transactionPendingPort.findByTxid(txid).orElse(null);
    }

    public List<PendingTransaction> getUserTransactions(Long userId) {
        return transactionPendingPort.findTransactionsByUserId(userId);
    }
}
