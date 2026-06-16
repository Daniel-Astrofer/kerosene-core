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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
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
        this.baseUrl = sanitizeBaseUrl(baseUrl);
        this.username = username;
        this.password = password;
        this.walletName = sanitizeWalletName(walletName);
    }

    @Override
    public JsonNode executeRpc(String method, Object... params) {
        return executeRpcAt(resolveEndpoint(), method, params);
    }

    public JsonNode executeNodeRpc(String method, Object... params) {
        return executeRpcAt(baseUrl, method, params);
    }

    private JsonNode executeRpcAt(String endpoint, String method, Object... params) {
        String rpcMethod = requireRpcMethod(method);
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "1.0");
            request.put("id", UUID.randomUUID().toString());
            request.put("method", rpcMethod);
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
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BitcoinCoreRpcException(
                        rpcMethod,
                        "Bitcoin Core RPC returned HTTP " + response.getStatusCode(),
                        (Integer) null);
            }

            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode error = body.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                Integer code = error.path("code").isIntegralNumber() ? error.path("code").asInt() : null;
                throw new BitcoinCoreRpcException(
                        rpcMethod,
                        error.path("message").asText("unknown error"),
                        code);
            }
            return body;
        } catch (BitcoinCoreRpcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BitcoinCoreRpcException(rpcMethod, "request failed", ex);
        }
    }

    @Override
    public String sendRawTransaction(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new IllegalArgumentException("raw transaction hex is required");
        }
        JsonNode result = unwrapResult(executeRpc("sendrawtransaction", hex));
        return result != null && !result.isNull() ? result.asText() : null;
    }

    @Override
    public JsonNode getRawTransaction(String txid, boolean verbose) {
        if (txid == null || txid.isBlank()) {
            throw new IllegalArgumentException("txid is required");
        }
        try {
            return unwrapResult(executeRpc("getrawtransaction", txid, verbose ? 1 : 0));
        } catch (RuntimeException rawTransactionFailure) {
            return walletTransaction(txid, rawTransactionFailure);
        }
    }

    public long getBlockCount() {
        JsonNode result = unwrapResult(executeNodeRpc("getblockcount"));
        return result != null && result.isNumber() ? result.asLong() : 0L;
    }

    public JsonNode getBlockchainInfo() {
        return unwrapResult(executeNodeRpc("getblockchaininfo"));
    }

    public String walletName() {
        return walletName;
    }

    public boolean loadConfiguredWallet() {
        if (walletName == null || walletName.isBlank()) {
            return false;
        }
        try {
            executeNodeRpc("loadwallet", walletName);
            return true;
        } catch (RuntimeException ex) {
            String message = exceptionMessageChain(ex).toLowerCase();
            if (message.contains("already loaded")) {
                return true;
            }
            throw ex;
        }
    }

    public RescanResult rescanBlockchain(long startHeight) {
        long safeStartHeight = Math.max(0L, startHeight);
        JsonNode result = unwrapResult(executeRpc("rescanblockchain", safeStartHeight));
        if (result == null || result.isNull() || result.isMissingNode()) {
            return new RescanResult(safeStartHeight, 0L);
        }
        return new RescanResult(
                result.path("start_height").asLong(safeStartHeight),
                result.path("stop_height").asLong(result.path("stopheight").asLong(0L)));
    }

    public String getNewAddress(String label) {
        JsonNode result = unwrapResult(executeRpc("getnewaddress", label != null ? label : "", "bech32"));
        String address = result != null && !result.isNull() ? result.asText() : "";
        if (address.isBlank()) {
            throw new IllegalStateException("Bitcoin Core did not return a new receiving address.");
        }
        return address;
    }

    private JsonNode walletTransaction(String txid, RuntimeException rawTransactionFailure) {
        try {
            return unwrapResult(executeRpc("gettransaction", txid, true, true));
        } catch (RuntimeException walletFailure) {
            rawTransactionFailure.addSuppressed(walletFailure);
            throw rawTransactionFailure;
        }
    }

    public FundedPsbt createFundedPsbt(String destinationAddress, long amountSats, Integer confirmationTarget) {
        requireText(destinationAddress, "destinationAddress");
        requirePositiveSats(amountSats, "amountSats");
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

    public void importWatchOnlyDescriptor(String descriptor, LocalDateTime timestamp) {
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor is required");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("desc", descriptor.trim());
        request.put("timestamp", "now");
        request.put("active", true);
        request.put("watchonly", true);
        if (descriptor.contains("*")) {
            request.put("range", List.of(0, 1000));
        }
        unwrapResult(executeRpc("importdescriptors", List.of(request)));
    }

    public FundedPsbt createWatchOnlyPsbt(
            List<PsbtInput> selectedInputs,
            String destinationAddress,
            long amountSats,
            Integer confirmationTarget,
            Long feeRateSatsPerVbyte) {
        if (selectedInputs == null || selectedInputs.isEmpty()) {
            throw new IllegalArgumentException("At least one selected input is required for watch-only PSBT creation.");
        }
        requireText(destinationAddress, "destinationAddress");
        requirePositiveSats(amountSats, "amountSats");
        List<Map<String, Object>> inputs = selectedInputs.stream()
                .map(input -> {
                    if (input == null || input.txid() == null || input.txid().isBlank() || input.vout() < 0) {
                        throw new IllegalArgumentException("selectedInputs must contain valid txid and vout values.");
                    }
                    return Map.<String, Object>of(
                            "txid", input.txid(),
                            "vout", input.vout());
                })
                .toList();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put(destinationAddress, satsToBtc(amountSats));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("includeWatching", true);
        options.put("add_inputs", false);
        options.put("change_type", "bech32");
        boolean explicitFeeRate = feeRateSatsPerVbyte != null && feeRateSatsPerVbyte > 0L;
        if (explicitFeeRate) {
            options.put("fee_rate", satsPerVbyteToBtcPerKvbyte(feeRateSatsPerVbyte));
        }
        if (!explicitFeeRate && confirmationTarget != null && confirmationTarget > 0) {
            options.put("conf_target", confirmationTarget);
        }

        JsonNode result = unwrapResult(executeRpc(
                "walletcreatefundedpsbt",
                inputs,
                List.of(output),
                0,
                options,
                true));

        String psbt = text(result, "psbt");
        long feeSats = btcNodeToSats(result.path("fee"));
        return new FundedPsbt(psbt, feeSats);
    }

    public JsonNode decodePsbt(String psbt) {
        return unwrapResult(executeRpc("decodepsbt", psbt));
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
        if (btc.signum() <= 0) {
            return 0L;
        }
        return btc.multiply(SATOSHIS_PER_BITCOIN)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    private BigDecimal satsPerVbyteToBtcPerKvbyte(long satsPerVbyte) {
        return BigDecimal.valueOf(satsPerVbyte)
                .multiply(BigDecimal.valueOf(1000L))
                .divide(SATOSHIS_PER_BITCOIN, 8, RoundingMode.UNNECESSARY);
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BITCOIN, 8, RoundingMode.UNNECESSARY);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String exceptionMessageChain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private String requireRpcMethod(String method) {
        String value = method != null ? method.trim() : "";
        if (value.isBlank()) {
            throw new IllegalArgumentException("Bitcoin Core RPC method is required");
        }
        return value;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void requirePositiveSats(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private String sanitizeBaseUrl(String url) {
        String trimmed = url != null ? url.trim() : "";
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("bitcoin.rpc.url is required");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("bitcoin.rpc.url must use http or https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("bitcoin.rpc.url must include a host");
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("bitcoin.rpc.url must not include userinfo credentials");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("bitcoin.rpc.url must not include query or fragment components");
            }
            String normalized = uri.normalize().toASCIIString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("bitcoin.rpc.url must be a valid URI", exception);
        }
    }

    private String sanitizeWalletName(String walletName) {
        String cleanWallet = walletName != null ? walletName.trim() : "";
        if (cleanWallet.isEmpty()) {
            return "";
        }
        if (!cleanWallet.matches("^[A-Za-z0-9._-]{1,64}$") || ".".equals(cleanWallet) || "..".equals(cleanWallet)) {
            throw new IllegalArgumentException(
                    "bitcoin.rpc.wallet may only contain letters, numbers, dots, underscores, and hyphens");
        }
        return URLEncoder.encode(cleanWallet, StandardCharsets.UTF_8);
    }

    public record FundedPsbt(String psbt, long feeSats) {
    }

    public record FinalizedPsbt(String hex, boolean complete) {
    }

    public record PsbtInput(String txid, int vout) {
    }

    public record RescanResult(long startHeight, long stopHeight) {
    }

    public static class BitcoinCoreRpcException extends IllegalStateException {

        private final String method;
        private final Integer code;

        public BitcoinCoreRpcException(String method, String message, Integer code) {
            super("Bitcoin Core RPC " + method + " failed"
                    + (code != null ? " (" + code + ")" : "")
                    + ": " + message);
            this.method = method;
            this.code = code;
        }

        public BitcoinCoreRpcException(String method, String message, Throwable cause) {
            super("Bitcoin Core RPC " + method + " failed: " + message, cause);
            this.method = method;
            this.code = null;
        }

        public String method() {
            return method;
        }

        public Integer code() {
            return code;
        }
    }
}
