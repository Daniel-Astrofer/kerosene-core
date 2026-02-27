package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AlchemyClient {

    private static final Logger log = LoggerFactory.getLogger(AlchemyClient.class);
    private final String alchemyUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AlchemyClient(
            @Value("${alchemy.bitcoin.url:https://btc-mainnet.g.alchemy.com/v2/}") String baseUrl,
            @Value("${alchemy.api-key:}") String apiKey) {
        this.alchemyUrl = baseUrl + apiKey;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes a JSON-RPC method on the Alchemy Bitcoin node.
     */
    public JsonNode executeRpc(String method, Object... params) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", method);

            ArrayNode paramsNode = objectMapper.createArrayNode();
            for (Object param : params) {
                if (param instanceof String)
                    paramsNode.add((String) param);
                else if (param instanceof Integer)
                    paramsNode.add((Integer) param);
                else if (param instanceof Long)
                    paramsNode.add((Long) param);
                else if (param instanceof Boolean)
                    paramsNode.add((Boolean) param);
                else if (param instanceof Double)
                    paramsNode.add((Double) param);
            }
            request.set("params", paramsNode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(alchemyUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Alchemy RPC error: HTTP {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error") && !root.get("error").isNull()) {
                log.error("Alchemy RPC method error: {}", root.get("error").toString());
                return null;
            }

            return root.get("result");
        } catch (Exception e) {
            log.error("Failed to execute Alchemy RPC: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Broadcasts a signed raw transaction hex to the Bitcoin network.
     */
    public String sendRawTransaction(String hex) {
        JsonNode result = executeRpc("sendrawtransaction", hex);
        return result != null ? result.asText() : null;
    }

    /**
     * Retrieves raw transaction data for a given TXID.
     */
    public JsonNode getRawTransaction(String txid, boolean verbose) {
        return executeRpc("getrawtransaction", txid, verbose ? 1 : 0);
    }
}
