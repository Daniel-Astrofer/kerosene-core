package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.transactions.application.transaction.TransactionMonitorPort;
import source.transactions.model.PendingTransaction;
import source.transactions.service.BlockchainMonitorService;

@Component
public class BlockchainMonitorAdapter implements TransactionMonitorPort {

    private final BlockchainMonitorService blockchainMonitorService;

    public BlockchainMonitorAdapter(BlockchainMonitorService blockchainMonitorService) {
        this.blockchainMonitorService = blockchainMonitorService;
    }

    @Override
    public void checkTransaction(PendingTransaction transaction) {
        blockchainMonitorService.checkTransaction(transaction);
    }
}
