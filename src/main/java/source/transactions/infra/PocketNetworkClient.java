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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pocket Network RPC Client.
 * Decentralized Bitcoin RPC Provider.
 */
@Component
public class PocketNetworkClient implements BlockchainClient {

    private static final Logger log = LoggerFactory.getLogger(PocketNetworkClient.class);
    private static final double MOCK_HOT_WALLET_BTC = 1.5d;
    private final String pocketUrl;
    private final boolean mockMode;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PocketNetworkClient(
            @Value("${pocket.bitcoin.url:https://bitcoin-mainnet.gateway.pokt.network/v1/lb/}") String baseUrl,
            @Value("${pocket.api-key:}") String gatewayToken,
            @Value("${bitcoin.mock-mode:false}") boolean mockMode) {
        this.pocketUrl = baseUrl + gatewayToken;
        this.mockMode = mockMode;
        // Timeouts prevent thread stalls when Pocket Network is slow or unreachable.
        // Connect: 5s (TCP handshake). Read: 15s (time for broadcast response).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        if (mockMode) {
            log.info("[PocketNetworkClient] bitcoin.mock-mode=true. Using deterministic local RPC responses.");
        }
    }

    @Override
    public JsonNode executeRpc(String method, Object... params) {
        if (mockMode) {
            JsonNode mockResponse = executeMockRpc(method, params);
            return mockResponse != null && mockResponse.has("result") ? mockResponse.get("result") : mockResponse;
        }

        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", method);

            ArrayNode paramsNode = objectMapper.createArrayNode();
            for (Object param : params) {
                paramsNode.add(objectMapper.valueToTree(param));
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

    private JsonNode executeMockRpc(String method, Object... params) {
        ObjectNode root = objectMapper.createObjectNode();

        switch (method) {
            case "getbalances" -> {
                ObjectNode result = root.putObject("result");
                ObjectNode mine = result.putObject("mine");
                mine.put("trusted", MOCK_HOT_WALLET_BTC);
                mine.put("untrusted_pending", 0);
                mine.put("immature", 0);
                return root;
            }
            case "getbalance" -> {
                root.put("result", MOCK_HOT_WALLET_BTC);
                return root;
            }
            case "estimatesmartfee" -> {
                int confirmationTarget = extractConfirmationTarget(params);
                ObjectNode result = root.putObject("result");
                result.put("feerate", mockFeeRateBtcPerKvB(confirmationTarget));
                return root;
            }
            case "listreceivedbyaddress" -> {
                root.set("result", objectMapper.createArrayNode());
                return root;
            }
            case "sendrawtransaction" -> {
                String payload = params.length > 0 && params[0] != null ? params[0].toString() : "mock";
                root.put("result", deterministicTxId(payload));
                return root;
            }
            case "getrawtransaction" -> {
                boolean verbose = params.length > 1 && params[1] instanceof Number number && number.intValue() != 0;
                if (verbose) {
                    ObjectNode result = root.putObject("result");
                    result.put("confirmations", 1);
                    result.put("txid", params.length > 0 && params[0] != null ? params[0].toString() : deterministicTxId("mock"));
                } else {
                    root.put("result", "00");
                }
                return root;
            }
            default -> {
                log.debug("[PocketNetworkClient] No mock response configured for RPC method {}", method);
                return null;
            }
        }
    }

    private int extractConfirmationTarget(Object... params) {
        if (params.length == 0 || !(params[0] instanceof Number number)) {
            return 6;
        }
        return number.intValue();
    }

    private double mockFeeRateBtcPerKvB(int confirmationTarget) {
        if (confirmationTarget <= 1) {
            return 0.0005d;
        }
        if (confirmationTarget <= 6) {
            return 0.00025d;
        }
        return 0.0001d;
    }

    private String deterministicTxId(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available for mock txid generation", e);
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
