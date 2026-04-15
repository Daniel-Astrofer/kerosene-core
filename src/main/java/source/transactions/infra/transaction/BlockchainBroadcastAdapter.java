package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.transactions.application.transaction.TransactionBroadcastPort;
import source.transactions.infra.BlockchainClient;

@Component
public class BlockchainBroadcastAdapter implements TransactionBroadcastPort {

    private final BlockchainClient blockchainClient;

    public BlockchainBroadcastAdapter(BlockchainClient blockchainClient) {
        this.blockchainClient = blockchainClient;
    }

    @Override
    public String sendRawTransaction(String rawTxHex) {
        return blockchainClient.sendRawTransaction(rawTxHex);
    }
}
