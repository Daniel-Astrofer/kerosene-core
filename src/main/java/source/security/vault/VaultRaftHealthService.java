package source.security.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VaultRaftHealthService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final boolean required;
    private final String baseUrl;
    private final String token;
    private final String tokenFile;
    private final int expectedServers;

    public VaultRaftHealthService(
            ObjectMapper objectMapper,
            @Value("${vault.raft.enabled:false}") boolean enabled,
            @Value("${vault.raft.required:false}") boolean required,
            @Value("${vault.raft.url:http://vault-raft-1:8200}") String baseUrl,
            @Value("${vault.raft.token:}") String token,
            @Value("${vault.raft.token-file:}") String tokenFile,
            @Value("${vault.raft.expected-servers:3}") int expectedServers) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.required = required;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token != null ? token.trim() : "";
        this.tokenFile = tokenFile != null ? tokenFile.trim() : "";
        this.expectedServers = Math.max(1, expectedServers);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @PostConstruct
    public void validateStartupQuorum() {
        VaultRaftSnapshot snapshot = snapshot();
        if (required && !"UP".equals(snapshot.status())) {
            throw new IllegalStateException("Vault Raft quorum is required but unavailable: " + snapshot.message());
        }
    }

    public VaultRaftSnapshot snapshot() {
        if (!enabled) {
            return new VaultRaftSnapshot(
                    "DISABLED",
                    false,
                    false,
                    false,
                    null,
                    0,
                    expectedServers,
                    List.of(),
                    Instant.now(),
                    "Vault Raft health check is disabled",
                    Map.of("required", required));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("url", baseUrl);
        details.put("required", required);
        try {
            JsonNode health = get("/v1/sys/health?standbyok=true&perfstandbyok=true", false);
            boolean initialized = health.path("initialized").asBoolean(false);
            boolean sealed = health.path("sealed").asBoolean(true);
            boolean standby = health.path("standby").asBoolean(false);

            String leaderAddress = null;
            try {
                JsonNode leader = get("/v1/sys/leader", true);
                leaderAddress = leader.path("leader_address").asText(null);
            } catch (Exception exception) {
                details.put("leaderProbe", exception.getClass().getSimpleName());
            }

            List<Map<String, Object>> servers = raftServers(details);
            long voters = servers.stream()
                    .filter(server -> Boolean.TRUE.equals(server.get("voter")))
                    .count();
            boolean quorum = initialized && !sealed && voters >= quorumThreshold(expectedServers);

            String status = quorum ? "UP" : "DOWN";
            String message = quorum
                    ? "Vault Raft cluster is initialized, unsealed, and has quorum"
                    : "Vault Raft cluster is not initialized, sealed, or below quorum";

            return new VaultRaftSnapshot(
                    status,
                    initialized,
                    sealed,
                    standby,
                    leaderAddress,
                    (int) voters,
                    expectedServers,
                    servers,
                    Instant.now(),
                    message,
                    details);
        } catch (Exception exception) {
            details.put("exception", exception.getClass().getSimpleName());
            return new VaultRaftSnapshot(
                    "DOWN",
                    false,
                    true,
                    false,
                    null,
                    0,
                    expectedServers,
                    List.of(),
                    Instant.now(),
                    "Vault Raft probe failed",
                    details);
        }
    }

    private List<Map<String, Object>> raftServers(Map<String, Object> details) throws IOException, InterruptedException {
        JsonNode configuration = get("/v1/sys/storage/raft/configuration", true);
        JsonNode serversNode = configuration.path("data").path("config").path("servers");
        List<Map<String, Object>> servers = new ArrayList<>();
        if (serversNode.isArray()) {
            for (JsonNode server : serversNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("nodeId", server.path("node_id").asText(""));
                entry.put("address", server.path("address").asText(""));
                entry.put("leader", server.path("leader").asBoolean(false));
                entry.put("voter", server.path("voter").asBoolean(false));
                servers.add(entry);
            }
        }
        details.put("serverCount", servers.size());
        return servers;
    }

    private JsonNode get(String path, boolean authenticated) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (authenticated) {
            String resolvedToken = resolveToken();
            if (resolvedToken.isBlank()) {
                throw new IOException("vault.raft.token or vault.raft.token-file is required for authenticated Raft checks");
            }
            request.header("X-Vault-Token", resolvedToken);
        }
        HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Vault Raft endpoint returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String resolveToken() throws IOException {
        if (!token.isBlank()) {
            return token;
        }
        if (!tokenFile.isBlank()) {
            return Files.readString(Path.of(tokenFile)).trim();
        }
        return "";
    }

    private int quorumThreshold(int nodes) {
        return (nodes / 2) + 1;
    }

    private String stripTrailingSlash(String value) {
        String trimmed = value != null ? value.trim() : "";
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public record VaultRaftSnapshot(
            String status,
            boolean initialized,
            boolean sealed,
            boolean standby,
            String leaderAddress,
            int votingServers,
            int expectedServers,
            List<Map<String, Object>> servers,
            Instant checkedAt,
            String message,
            Map<String, Object> details) {
    }
}
