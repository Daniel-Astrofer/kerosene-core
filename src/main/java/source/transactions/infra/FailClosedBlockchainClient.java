package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(BlockchainClient.class)
@ConditionalOnExpression("'${bitcoin.rpc.enabled:false}' == 'false' && '${bitcoin.esplora.enabled:false}' == 'false'")
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
