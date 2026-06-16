package source.transactions.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import source.transactions.application.transaction.TransactionPendingPort;
import source.transactions.application.transaction.monitoring.MonitorPendingTransactionUseCase;
import source.transactions.monitoring.BitcoinBlockchainMonitorService;
import source.transactions.monitoring.LightningNetworkMonitorService;
import source.transactions.model.PendingTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class BlockchainMonitorService {

    private final TransactionPendingPort transactionPendingPort;
    private final MonitorPendingTransactionUseCase monitorPendingTransactionUseCase;
    private final ObjectProvider<BitcoinBlockchainMonitorService> bitcoinMonitorService;
    private final ObjectProvider<LightningNetworkMonitorService> lightningMonitorService;

    public BlockchainMonitorService(
            TransactionPendingPort transactionPendingPort,
            MonitorPendingTransactionUseCase monitorPendingTransactionUseCase,
            ObjectProvider<BitcoinBlockchainMonitorService> bitcoinMonitorService,
            ObjectProvider<LightningNetworkMonitorService> lightningMonitorService) {
        this.transactionPendingPort = transactionPendingPort;
        this.monitorPendingTransactionUseCase = monitorPendingTransactionUseCase;
        this.bitcoinMonitorService = bitcoinMonitorService;
        this.lightningMonitorService = lightningMonitorService;
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

    public BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot blockchainStatus() {
        BitcoinBlockchainMonitorService service = bitcoinMonitorService.getIfAvailable();
        if (service == null) {
            return null;
        }
        return service.snapshot();
    }

    public LightningNetworkMonitorService.LightningMonitorSnapshot lightningStatus() {
        LightningNetworkMonitorService service = lightningMonitorService.getIfAvailable();
        if (service == null) {
            return null;
        }
        return service.snapshot();
    }

    public BlockchainVisualizationSnapshot visualizationSnapshot() {
        return new BlockchainVisualizationSnapshot(blockchainStatus(), lightningStatus());
    }

    public Map<String, Object> triggerBlockchainSync() {
        BitcoinBlockchainMonitorService service = bitcoinMonitorService.getIfAvailable();
        if (service == null) {
            return Map.of("status", "SKIPPED", "reason", "NO_BITCOIN_MONITOR");
        }
        return service.triggerSyncSearch();
    }

    public record BlockchainVisualizationSnapshot(
            BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot blockchain,
            LightningNetworkMonitorService.LightningMonitorSnapshot lightning) {
    }
}
