package source.transactions.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Client to interact with an explicitly configured local mempool-compatible
 * endpoint. Public APIs are not used implicitly.
 */
@Component
public class MempoolClient {

    private static final Logger log = LoggerFactory.getLogger(MempoolClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String mempoolUrl;

    public MempoolClient(
            @Qualifier("mempoolHttpClient") HttpClient httpClient,
            @Value("${bitcoin.fee-recommendation.url:${operational.health.mempool-url:}}") String mempoolUrl,
            ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.mempoolUrl = mempoolUrl != null ? mempoolUrl.trim() : "";
    }

    public record RecommendedFees(long fastestFee, long halfHourFee, long hourFee, long economyFee) {}

    public RecommendedFees getRecommendedFees() {
        if (mempoolUrl.isBlank()) {
            log.warn("[Mempool] No local fee recommendation endpoint configured. Falling back to conservative static fees.");
            return fallbackFees();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mempoolUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());

                return new RecommendedFees(
                    node.get("fastestFee").asLong(),
                    node.get("halfHourFee").asLong(),
                    node.get("hourFee").asLong(),
                    node.get("economyFee").asLong()
                );
            }
        } catch (Exception e) {
            log.error("[Mempool] Failed to fetch fees: {}. Falling back to safe defaults.", e.getMessage());
        }

        return fallbackFees();
    }

    private RecommendedFees fallbackFees() {
        return new RecommendedFees(50, 40, 30, 20);
    }
}
