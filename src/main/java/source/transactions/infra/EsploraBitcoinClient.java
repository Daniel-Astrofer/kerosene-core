package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import source.common.service.AddressDerivationService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bitcoin client backed by the Esplora HTTP API.
 * Works with public endpoints or a self-hosted Esplora instance and does not
 * require an API key by default.
 */
@Component
public class EsploraBitcoinClient implements BlockchainClient {

    private static final Logger log = LoggerFactory.getLogger(EsploraBitcoinClient.class);
    private static final double MOCK_HOT_WALLET_BTC = 1.5d;

    private final String esploraBaseUrl;
    private final String hotWalletAddress;
    private final String hotWalletXpub;
    private final int hotWalletXpubScanRange;
    private final boolean mockMode;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AddressDerivationService addressDerivationService;
    private final AtomicBoolean missingHotWalletWarningLogged = new AtomicBoolean(false);

    public EsploraBitcoinClient(
            @Value("${bitcoin.esplora.base-url:}") String configuredBaseUrl,
            @Value("${bitcoin.network:mainnet}") String network,
            @Value("${bitcoin.hot-wallet.address:}") String hotWalletAddress,
            @Value("${bitcoin.hot-wallet.xpub:}") String hotWalletXpub,
            @Value("${bitcoin.hot-wallet.xpub-scan-range:128}") int hotWalletXpubScanRange,
            @Value("${bitcoin.mock-mode:false}") boolean mockMode,
            @Qualifier("esploraRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AddressDerivationService addressDerivationService) {
        this.esploraBaseUrl = sanitizeBaseUrl(resolveBaseUrl(configuredBaseUrl, network));
        this.hotWalletAddress = normalize(hotWalletAddress);
        this.hotWalletXpub = normalize(hotWalletXpub);
        this.hotWalletXpubScanRange = Math.max(1, hotWalletXpubScanRange);
        this.mockMode = mockMode;
        this.addressDerivationService = addressDerivationService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;

        if (mockMode) {
            log.info("[EsploraBitcoinClient] bitcoin.mock-mode=true. Using deterministic local responses.");
        } else {
            log.info("[EsploraBitcoinClient] Using Esplora base URL {}", this.esploraBaseUrl);
        }
    }

    @Override
    public JsonNode executeRpc(String method, Object... params) {
        if (method == null || method.isBlank()) {
            return null;
        }

        try {
            return switch (method) {
                case "sendrawtransaction" -> wrapResult(sendRawTransaction(firstParamAsText(params)));
                case "getrawtransaction" -> {
                    boolean verbose = params.length > 1 && params[1] instanceof Number number && number.intValue() != 0;
                    yield getRawTransaction(firstParamAsText(params), verbose);
                }
                case "listreceivedbyaddress" -> {
                    String address = params.length > 3 ? String.valueOf(params[3]) : null;
                    yield getAddressTransactions(address);
                }
                default -> {
                    log.debug("[EsploraBitcoinClient] Unsupported executeRpc method {}", method);
                    yield null;
                }
            };
        } catch (RuntimeException ex) {
            log.warn("[EsploraBitcoinClient] RPC compatibility method {} failed: {}", method, ex.getMessage());
            return null;
        }
    }

    @Override
    public long getHotWalletBalance() {
        if (mockMode) {
            return btcToSats(MOCK_HOT_WALLET_BTC);
        }

        if (hotWalletXpub != null) {
            return getConfirmedBalanceForXpub(hotWalletXpub, hotWalletXpubScanRange, true);
        }

        if (hotWalletAddress != null) {
            return getConfirmedBalanceForAddress(hotWalletAddress);
        }

        if (missingHotWalletWarningLogged.compareAndSet(false, true)) {
            log.warn("[EsploraBitcoinClient] Hot wallet tracker not configured. Set bitcoin.hot-wallet.address or bitcoin.hot-wallet.xpub.");
        }
        return 0L;
    }

    @Override
    public FeeRates estimateSmartFee(int fastBlocks, int halfHourBlocks, int hourBlocks) {
        if (mockMode) {
            return new FeeRates(50L, 25L, 10L);
        }

        try {
            JsonNode feeEstimates = readJson(getUrl("/fee-estimates"));
            return new FeeRates(
                    resolveFeeRate(feeEstimates, fastBlocks, 50L),
                    resolveFeeRate(feeEstimates, halfHourBlocks, 25L),
                    resolveFeeRate(feeEstimates, hourBlocks, 10L));
        } catch (Exception ex) {
            log.warn("[EsploraBitcoinClient] Fee estimate lookup failed: {}", ex.getMessage());
            return new FeeRates(50L, 25L, 10L);
        }
    }

    @Override
    public JsonNode getAddressTransactions(String address) {
        if (address == null || address.isBlank()) {
            return objectMapper.createArrayNode();
        }
        if (mockMode) {
            return objectMapper.createArrayNode();
        }

        try {
            JsonNode transactions = readJson(getUrl("/address/" + address + "/txs"));
            return transactions != null && transactions.isArray() ? transactions : objectMapper.createArrayNode();
        } catch (HttpClientErrorException.NotFound ex) {
            return objectMapper.createArrayNode();
        } catch (Exception ex) {
            log.warn("[EsploraBitcoinClient] Address transaction lookup failed for {}: {}", address, ex.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    @Override
    public long getConfirmedBalanceForAddress(String address) {
        if (address == null || address.isBlank()) {
            return 0L;
        }
        if (mockMode) {
            return 0L;
        }

        try {
            JsonNode summary = readJson(getUrl("/address/" + address));
            JsonNode chainStats = summary.path("chain_stats");
            long funded = asLong(chainStats.path("funded_txo_sum"));
            long spent = asLong(chainStats.path("spent_txo_sum"));
            return Math.max(0L, funded - spent);
        } catch (HttpClientErrorException.NotFound ex) {
            return 0L;
        } catch (Exception ex) {
            log.warn("[EsploraBitcoinClient] Address balance lookup failed for {}: {}", address, ex.getMessage());
            return 0L;
        }
    }

    @Override
    public long getConfirmedBalanceForXpub(String xpub, int range, boolean includeChangeBranch) {
        if (xpub == null || xpub.isBlank()) {
            return 0L;
        }
        if (mockMode) {
            return 0L;
        }

        int safeRange = Math.max(1, range);
        long total = 0L;
        for (int index = 0; index < safeRange; index++) {
            total += getConfirmedBalanceForAddress(addressDerivationService.deriveAddressFromXpub(xpub, index, false));
            if (includeChangeBranch) {
                total += getConfirmedBalanceForAddress(addressDerivationService.deriveAddressFromXpub(xpub, index, true));
            }
        }
        return total;
    }

    @Override
    public java.util.List<AddressUtxo> getUnspentOutputs(String address) {
        if (address == null || address.isBlank()) {
            return java.util.List.of();
        }
        if (mockMode) {
            return java.util.List.of(new AddressUtxo(
                    deterministicTxId("mock-utxo:" + address),
                    0,
                    btcToSats(MOCK_HOT_WALLET_BTC),
                    null));
        }

        try {
            JsonNode utxos = readJson(getUrl("/address/" + address + "/utxo"));
            if (utxos == null || !utxos.isArray()) {
                return java.util.List.of();
            }

            java.util.List<AddressUtxo> results = new ArrayList<>();
            for (JsonNode utxo : utxos) {
                String txid = textValue(utxo, "txid");
                JsonNode vout = utxo.path("vout");
                JsonNode value = utxo.path("value");
                if (txid != null && vout.isIntegralNumber() && value.isIntegralNumber() && value.asLong() > 0L) {
                    results.add(new AddressUtxo(txid, vout.asInt(), value.asLong(), textValue(utxo, "scriptpubkey")));
                }
            }
            return results;
        } catch (HttpClientErrorException.NotFound ex) {
            return java.util.List.of();
        } catch (Exception ex) {
            log.warn("[EsploraBitcoinClient] UTXO lookup failed for {}: {}", address, ex.getMessage());
            return java.util.List.of();
        }
    }

    @Override
    public String sendRawTransaction(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        if (mockMode) {
            return deterministicTxId(hex);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(hex, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(getUrl("/tx"), entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("[EsploraBitcoinClient] Broadcast failed with HTTP {}", response.getStatusCode());
                return null;
            }

            String body = response.getBody();
            return body != null ? body.trim() : null;
        } catch (Exception ex) {
            log.warn("[EsploraBitcoinClient] Broadcast failed: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public JsonNode getRawTransaction(String txid, boolean verbose) {
        if (txid == null || txid.isBlank()) {
            return null;
        }
        if (mockMode) {
            return executeMockGetRawTransaction(txid, verbose);
        }

        try {
            if (!verbose) {
                String hex = restTemplate.getForObject(getUrl("/tx/" + txid + "/hex"), String.class);
                return hex != null ? objectMapper.getNodeFactory().textNode(hex) : null;
            }

            JsonNode transaction = readJson(getUrl("/tx/" + txid));
            if (transaction == null || !transaction.isObject()) {
                return null;
            }

            ObjectNode enriched = ((ObjectNode) transaction).deepCopy();
            JsonNode status = enriched.path("status");
            if (status.path("confirmed").asBoolean(false)) {
                long tipHeight = readLong(getUrl("/blocks/tip/height"));
                long blockHeight = asLong(status.path("block_height"));
                long confirmations = blockHeight > 0L && tipHeight >= blockHeight
                        ? (tipHeight - blockHeight) + 1L
                        : 0L;
                enriched.put("confirmations", confirmations);
            } else {
                enriched.put("confirmations", 0);
            }
            return enriched;
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        } catch (Exception ex) {
            log.warn("[EsploraBitcoinClient] Transaction lookup failed for {}: {}", txid, ex.getMessage());
            return null;
        }
    }

    private JsonNode executeMockGetRawTransaction(String txid, boolean verbose) {
        if (!verbose) {
            return objectMapper.getNodeFactory().textNode("00");
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("txid", txid);
        result.put("confirmations", 1);
        return result;
    }

    private JsonNode readJson(String url) throws Exception {
        String body = restTemplate.getForObject(url, String.class);
        return body != null && !body.isBlank() ? objectMapper.readTree(body) : null;
    }

    private long readLong(String url) {
        String body = restTemplate.getForObject(url, String.class);
        if (body == null || body.isBlank()) {
            return 0L;
        }
        return Long.parseLong(body.trim());
    }

    private long resolveFeeRate(JsonNode feeEstimates, int targetBlocks, long fallback) {
        if (feeEstimates == null || feeEstimates.isMissingNode() || feeEstimates.isNull()) {
            return fallback;
        }

        int safeTarget = targetBlocks <= 0 ? 1 : targetBlocks;
        double bestRate = -1d;
        int bestTarget = Integer.MAX_VALUE;
        double highestRate = -1d;

        for (Map.Entry<String, JsonNode> entry : iterable(feeEstimates.fields())) {
            int availableTarget;
            try {
                availableTarget = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ignored) {
                continue;
            }

            JsonNode value = entry.getValue();
            if (!value.isNumber()) {
                continue;
            }

            double rate = value.asDouble();
            if (rate <= 0d) {
                continue;
            }

            if (availableTarget >= safeTarget && availableTarget < bestTarget) {
                bestTarget = availableTarget;
                bestRate = rate;
            }
            if (availableTarget > 0 && rate > highestRate) {
                highestRate = rate;
            }
        }

        double selectedRate = bestRate > 0d ? bestRate : highestRate;
        return selectedRate > 0d ? Math.max(1L, (long) Math.ceil(selectedRate)) : fallback;
    }

    private Iterable<Map.Entry<String, JsonNode>> iterable(java.util.Iterator<Map.Entry<String, JsonNode>> iterator) {
        return () -> iterator;
    }

    private JsonNode wrapResult(String value) {
        if (value == null) {
            return null;
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("result", value);
        return root;
    }

    private String firstParamAsText(Object... params) {
        return params.length > 0 && params[0] != null ? String.valueOf(params[0]) : null;
    }

    private long asLong(JsonNode node) {
        return node != null && node.isNumber() ? node.asLong() : 0L;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text != null && !text.isBlank() ? text : null;
    }

    private long btcToSats(double btc) {
        return Math.max(0L, Math.round(btc * 100_000_000d));
    }

    private String getUrl(String path) {
        return esploraBaseUrl + path;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sanitizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("bitcoin.esplora.base-url resolved to an empty value");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String resolveBaseUrl(String configuredBaseUrl, String network) {
        String explicit = normalize(configuredBaseUrl);
        if (explicit != null) {
            return explicit;
        }

        String normalizedNetwork = normalize(network);
        if (normalizedNetwork == null) {
            return "https://blockstream.info/api";
        }

        return switch (normalizedNetwork.toLowerCase(Locale.ROOT)) {
            case "testnet" -> "https://blockstream.info/testnet/api";
            case "signet" -> "https://blockstream.info/signet/api";
            default -> "https://blockstream.info/api";
        };
    }

    private String deterministicTxId(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available for mock txid generation", e);
        }
    }
}
