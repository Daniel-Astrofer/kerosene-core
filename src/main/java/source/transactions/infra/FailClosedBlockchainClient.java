package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"bitcoin.rpc.enabled", "bitcoin.esplora.enabled"}, havingValue = "false", matchIfMissing = true)
public class FailClosedBlockchainClient implements BlockchainClient {

    @Override
    public JsonNode executeRpc(String method, Object... params) {
        throw unavailable();
    }

    @Override
    public String sendRawTransaction(String hex) {
        throw unavailable();
    }

    @Override
    public JsonNode getRawTransaction(String txid, boolean verbose) {
        throw unavailable();
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException(
                "No Bitcoin provider is configured. Enable bitcoin.rpc.enabled for Bitcoin Core "
                        + "or explicitly configure bitcoin.esplora.enabled with a local endpoint.");
    }
}
