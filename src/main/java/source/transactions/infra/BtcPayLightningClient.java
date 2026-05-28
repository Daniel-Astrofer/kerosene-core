package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import source.transactions.service.BitcoinNodeService;

/**
 * BTCPay Server / Lightning balance adapter.
 *
 * If no live endpoint is configured, balances fall back to zero rather than
 * inventing reserves.
 */
@Component
@ConditionalOnMissingBean(BitcoinNodeService.class)
public class BtcPayLightningClient implements LightningClient {

    private static final Logger log = LoggerFactory.getLogger(BtcPayLightningClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String localBalancePath;
    private final String remoteBalancePath;
    private final String nodeBalancePath;
    private final String uptimePath;
    private final String latencyPath;

    public BtcPayLightningClient(
            @Value("${lightning.provider.base-url:}") String baseUrl,
            @Value("${lightning.provider.api-key:}") String apiKey,
            @Value("${lightning.provider.local-balance-path:/api/v1/lightning/local-balance}") String localBalancePath,
            @Value("${lightning.provider.remote-balance-path:/api/v1/lightning/remote-balance}") String remoteBalancePath,
            @Value("${lightning.provider.node-balance-path:/api/v1/lightning/node-balance}") String nodeBalancePath,
            @Value("${lightning.provider.uptime-path:/api/v1/lightning/uptime}") String uptimePath,
            @Value("${lightning.provider.latency-path:/api/v1/lightning/latency}") String latencyPath,
            @Qualifier("lightningRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = sanitize(baseUrl);
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.localBalancePath = localBalancePath;
        this.remoteBalancePath = remoteBalancePath;
        this.nodeBalancePath = nodeBalancePath;
        this.uptimePath = uptimePath;
        this.latencyPath = latencyPath;
    }

    @Override
    public long getLocalBalance() {
        if (!isLive()) {
            return 0L;
        }
        JsonNode response = get(localBalancePath);
        return longField(response, "localBalanceSats", "balanceSats", "local_balance_sat", "localBalance");
    }

    @Override
    public long getRemoteBalance() {
        if (!isLive()) {
            return 0L;
        }
        JsonNode response = get(remoteBalancePath);
        return longField(response, "remoteBalanceSats", "balanceSats", "remote_balance_sat", "remoteBalance");
    }

    @Override
    public long getLightningNodeBalance() {
        if (!isLive()) {
            return 0L;
        }

        JsonNode response = get(nodeBalancePath);
        long explicit = longField(response, "nodeBalanceSats", "totalBalanceSats", "balanceSats", "balance");
        if (explicit > 0) {
            return explicit;
        }
        return getLocalBalance() + getRemoteBalance();
    }

    @Override
    public double getNodeUptime() {
        if (!isLive()) {
            return 1.0d;
        }
        JsonNode response = get(uptimePath);
        double value = doubleField(response, "uptime", "uptimeRatio", "availability");
        if (value > 1.0d) {
            return value / 100.0d;
        }
        return value > 0 ? value : 1.0d;
    }

    @Override
    public long getLspLatency() {
        if (!isLive()) {
            return 0L;
        }
        JsonNode response = get(latencyPath);
        return longField(response, "latencyMs", "latency", "lspLatencyMs");
    }

    private boolean isLive() {
        return !baseUrl.isBlank() && !apiKey.isBlank();
    }

    private JsonNode get(String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(baseUrl + path, HttpMethod.GET, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            log.warn("[LightningClient] Failed to query {}: {}", path, ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private long longField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0L;
    }

    private double doubleField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0d;
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
