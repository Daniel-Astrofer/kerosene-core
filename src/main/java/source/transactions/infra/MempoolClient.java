package source.transactions.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Client to interact with mempool.space or local mempool instance (Agente 4).
 * Suggests Low, Medium, High fees based on current block space demand.
 */
@Component
public class MempoolClient {

    private static final Logger log = LoggerFactory.getLogger(MempoolClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String mempoolUrl = "https://mempool.space/api/v1/fees/recommended";

    public MempoolClient(
            @Qualifier("mempoolHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public record RecommendedFees(long fastestFee, long halfHourFee, long hourFee, long economyFee) {}

    public RecommendedFees getRecommendedFees() {
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

        // Safe defaults if API is down
        return new RecommendedFees(50, 40, 30, 20);
    }
}
