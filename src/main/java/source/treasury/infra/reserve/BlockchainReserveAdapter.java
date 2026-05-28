package source.treasury.infra.reserve;

import org.springframework.stereotype.Component;
import source.transactions.infra.BlockchainClient;
import source.treasury.application.port.out.BlockchainReservePort;

@Component
public class BlockchainReserveAdapter implements BlockchainReservePort {

    private final BlockchainClient blockchainClient;

    public BlockchainReserveAdapter(BlockchainClient blockchainClient) {
        this.blockchainClient = blockchainClient;
    }

    @Override
    public long getHotWalletBalance() {
        return blockchainClient.getHotWalletBalance();
    }

    @Override
    public long getConfirmedBalanceForXpub(String xpub, int scanRange, boolean includeChange) {
        return blockchainClient.getConfirmedBalanceForXpub(xpub, scanRange, includeChange);
    }

    @Override
    public long getConfirmedBalanceForAddress(String address) {
        return blockchainClient.getConfirmedBalanceForAddress(address);
    }
}
