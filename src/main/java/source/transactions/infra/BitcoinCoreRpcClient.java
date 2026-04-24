package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Primary
@Component
@ConditionalOnProperty(prefix = "bitcoin.rpc", name = "enabled", havingValue = "true")
public class BitcoinCoreRpcClient implements BlockchainClient {

    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String walletName;

    public BitcoinCoreRpcClient(
            @Qualifier("bitcoindRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${bitcoin.rpc.url}") String baseUrl,
            @Value("${bitcoin.rpc.username}") String username,
            @Value("${bitcoin.rpc.password}") String password,
            @Value("${bitcoin.rpc.wallet:}") String walletName) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = sanitize(baseUrl);
        this.username = username;
        this.password = password;
        this.walletName = walletName != null ? walletName.trim() : "";
    }

    @Override
    public JsonNode executeRpc(String method, Object... params) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "1.0");
            request.put("id", UUID.randomUUID().toString());
            request.put("method", method);
            ArrayNode array = request.putArray("params");
            if (params != null) {
                for (Object param : params) {
                    array.add(objectMapper.valueToTree(param));
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, basicAuthHeader());
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(resolveEndpoint(), entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Bitcoin Core RPC returned HTTP " + response.getStatusCode());
            }

            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode error = body.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException(
                        "Bitcoin Core RPC " + method + " failed: " + error.path("message").asText("unknown error"));
            }
            return body;
        } catch (Exception ex) {
            throw new IllegalStateException("Bitcoin Core RPC request failed for method " + method, ex);
        }
    }

    @Override
    public String sendRawTransaction(String hex) {
        JsonNode result = unwrapResult(executeRpc("sendrawtransaction", hex));
        return result != null && !result.isNull() ? result.asText() : null;
    }

    @Override
    public JsonNode getRawTransaction(String txid, boolean verbose) {
        return unwrapResult(executeRpc("getrawtransaction", txid, verbose ? 1 : 0));
    }

    public long getBlockCount() {
        JsonNode result = unwrapResult(executeRpc("getblockcount"));
        return result != null && result.isNumber() ? result.asLong() : 0L;
    }

    public FundedPsbt createFundedPsbt(String destinationAddress, long amountSats, Integer confirmationTarget) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(destinationAddress, satsToBtc(amountSats));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("includeWatching", true);
        options.put("change_type", "bech32");
        if (confirmationTarget != null && confirmationTarget > 0) {
            options.put("conf_target", confirmationTarget);
        }

        JsonNode result = unwrapResult(executeRpc(
                "walletcreatefundedpsbt",
                List.of(),
                List.of(output),
                0,
                options,
                true));

        String psbt = text(result, "psbt");
        long feeSats = btcNodeToSats(result.path("fee"));
        return new FundedPsbt(psbt, feeSats);
    }

    public String combinePsbt(List<String> partialPsbts) {
        JsonNode result = unwrapResult(executeRpc("combinepsbt", partialPsbts));
        return result != null && !result.isNull() ? result.asText() : null;
    }

    public FinalizedPsbt finalizePsbt(String psbt) {
        JsonNode result = unwrapResult(executeRpc("finalizepsbt", psbt, true));
        return new FinalizedPsbt(
                text(result, "hex"),
                result.path("complete").asBoolean(false));
    }

    public JsonNode decodeRawTransaction(String rawHex) {
        return unwrapResult(executeRpc("decoderawtransaction", rawHex));
    }

    private String resolveEndpoint() {
        if (walletName == null || walletName.isBlank()) {
            return baseUrl;
        }
        return baseUrl + "/wallet/" + walletName;
    }

    private String basicAuthHeader() {
        String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode unwrapResult(JsonNode response) {
        if (response != null && response.has("result")) {
            return response.get("result");
        }
        return response;
    }

    private long btcNodeToSats(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return 0L;
        }
        BigDecimal btc = value.isNumber()
                ? value.decimalValue()
                : new BigDecimal(value.asText("0"));
        return btc.multiply(SATOSHIS_PER_BITCOIN)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BITCOIN, 8, RoundingMode.HALF_UP);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String sanitize(String url) {
        String trimmed = url != null ? url.trim() : "";
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record FundedPsbt(String psbt, long feeSats) {
    }

    public record FinalizedPsbt(String hex, boolean complete) {
    }
}
