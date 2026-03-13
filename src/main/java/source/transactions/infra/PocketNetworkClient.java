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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Pocket Network RPC Client.
 * Decentralized Bitcoin RPC Provider.
 */
@Component
public class PocketNetworkClient implements BlockchainClient {

    private static final Logger log = LoggerFactory.getLogger(PocketNetworkClient.class);
    private final String pocketUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PocketNetworkClient(
            @Value("${pocket.bitcoin.url:https://bitcoin-mainnet.gateway.pokt.network/v1/lb/}") String baseUrl,
            @Value("${pocket.api-key:}") String gatewayToken) {
        this.pocketUrl = baseUrl + gatewayToken;
        // Timeouts prevent thread stalls when Pocket Network is slow or unreachable.
        // Connect: 5s (TCP handshake). Read: 15s (time for broadcast response).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @Override
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
            ResponseEntity<String> response = restTemplate.postForEntity(pocketUrl, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Pocket RPC error: HTTP {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.get("result");
        } catch (Exception e) {
            log.warn("Pocket Network RPC failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String sendRawTransaction(String hex) {
        JsonNode result = executeRpc("sendrawtransaction", hex);
        return result != null ? result.asText() : null;
    }

    @Override
    public JsonNode getRawTransaction(String txid, boolean verbose) {
        return executeRpc("getrawtransaction", txid, verbose ? 1 : 0);
    }
}
