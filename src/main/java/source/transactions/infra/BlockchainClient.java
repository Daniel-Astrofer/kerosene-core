package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;

public interface BlockchainClient {
    JsonNode executeRpc(String method, Object... params);

    String sendRawTransaction(String hex);

    JsonNode getRawTransaction(String txid, boolean verbose);
}
