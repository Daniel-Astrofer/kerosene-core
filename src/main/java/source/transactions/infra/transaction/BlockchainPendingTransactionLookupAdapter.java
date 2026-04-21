package source.transactions.infra.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import source.transactions.application.transaction.monitoring.PendingTransactionBlockchainPort;
import source.transactions.application.transaction.monitoring.TransactionMonitoringRateLimitException;
import source.transactions.infra.BlockchainClient;

import java.util.Locale;
import java.util.Optional;

@Component
public class BlockchainPendingTransactionLookupAdapter implements PendingTransactionBlockchainPort {

    private final BlockchainClient blockchainClient;

    public BlockchainPendingTransactionLookupAdapter(BlockchainClient blockchainClient) {
        this.blockchainClient = blockchainClient;
    }

    @Override
    public Optional<BlockchainTransactionSnapshot> loadTransaction(String txid) {
        try {
            JsonNode txInfo = blockchainClient.getRawTransaction(txid, true);
            if (txInfo == null || txInfo.isNull() || txInfo.isMissingNode()) {
                return Optional.empty();
            }

            int confirmations = txInfo.path("confirmations").isNumber()
                    ? txInfo.path("confirmations").asInt()
                    : 0;
            return Optional.of(new BlockchainTransactionSnapshot(confirmations));
        } catch (RuntimeException ex) {
            if (isRateLimited(ex)) {
                throw new TransactionMonitoringRateLimitException(
                        "Rate limited while loading transaction " + txid,
                        ex);
            }
            throw ex;
        }
    }

    private boolean isRateLimited(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("rate") || normalized.contains("429") || normalized.contains("too many");
    }
}
