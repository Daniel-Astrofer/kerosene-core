package source.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Performs the node attestation exchange with Vault.
 */
@Component
public class VaultAttestationClient {

    private static final Logger logger = LoggerFactory.getLogger(VaultAttestationClient.class);

    private final ShardIdentityManager shardIdentityManager;
    private final ObjectMapper objectMapper;
    private final HttpClient directClient;

    @Value("${vault.proxy.host:}")
    private String proxyHost;

    @Value("${vault.proxy.port:0}")
    private int proxyPort;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    @Value("${vault.request-timeout-ms:75000}")
    private int vaultRequestTimeoutMs;

    @Value("${shard.attestation.secret:}")
    private String shardAttestationSecret;

    public VaultAttestationClient(ShardIdentityManager shardIdentityManager, ObjectMapper objectMapper) {
        this.shardIdentityManager = shardIdentityManager;
        this.objectMapper = objectMapper;
        this.directClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public VaultAttestationSession attest(String resolvedVaultUrl) throws IOException, InterruptedException {
        String nodeId = shardIdentityManager.getStableNodeId();
        String publicKeyBase64 = shardIdentityManager.getPublicKeyBase64();
        String tpmQuote = obtainContainerAttestationQuote(nodeId, publicKeyBase64);
        logger.info("[VaultAttestationClient] Container attestation quote prepared. Node: {}. Attesting...", nodeId);

        String attestBody = objectMapper.writeValueAsString(Map.of(
                "tpm_quote", tpmQuote,
                "node_id", nodeId,
                "public_key", publicKeyBase64));

        String sessionToken;
        if (proxyPath != null && !proxyPath.isBlank()) {
            sessionToken = attestViaUds(resolvedVaultUrl, nodeId, attestBody);
        } else {
            sessionToken = attestDirectly(resolvedVaultUrl, nodeId, attestBody);
        }

        return new VaultAttestationSession(nodeId, sessionToken.trim());
    }

    private String attestViaUds(String resolvedVaultUrl, String nodeId, String attestBody) throws IOException {
        logger.debug("[VaultAttestationClient] Routing attestation via UDS SOCKS5 at: {}", proxyPath);
        UdsSocks5Transport transport = new UdsSocks5Transport(proxyPath, vaultRequestTimeoutMs);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Node-Id", nodeId);

        UdsSocks5Transport.HttpResult result = transport.executeHttpRequest(
                resolvedVaultUrl + "/v1/vault/attest",
                "POST",
                attestBody,
                headers);

        if (result.statusCode() != 200) {
            throw new VaultAttestationException("Vault rejected attestation (UDS): " + result.bodyAsString());
        }

        logger.info("[VaultAttestationClient] Hardware attested via UDS. Session token received.");
        return result.bodyAsString();
    }

    private String attestDirectly(String resolvedVaultUrl, String nodeId, String attestBody)
            throws IOException, InterruptedException {
        assertDirectConnectionAllowed();
        logger.warn("[VaultAttestationClient] DEV MODE - connecting to Vault directly without Tor.");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolvedVaultUrl + "/v1/vault/attest"))
                .header("Content-Type", "application/json")
                .header("X-Node-Id", nodeId)
                .timeout(Duration.ofMillis(vaultRequestTimeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(attestBody))
                .build();

        HttpResponse<String> response = directClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new VaultAttestationException("Vault rejected attestation: " + response.body());
        }

        logger.info("[VaultAttestationClient] Hardware attested. Session token received.");
        return response.body();
    }

    private void assertDirectConnectionAllowed() {
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            logger.error("[VaultAttestationClient] CRITICAL: vault.proxy.host={} is set but vault.proxy.path is not.",
                    proxyHost);
            logger.error("[VaultAttestationClient] Java ProxySelector would resolve .onion via local DNS.");
            logger.error("[VaultAttestationClient] Set vault.proxy.path to the Tor UDS socket path instead.");
            throw new VaultAttestationException(
                    "DNS leak prevention: vault.proxy.path (UDS socket) is required when connecting to .onion URLs. "
                            + "Configure vault.proxy.path=/var/run/tor/socks/tor.sock");
        }
    }

    private String obtainContainerAttestationQuote(String nodeId, String publicKeyBase64) {
        if (shardAttestationSecret == null || shardAttestationSecret.isBlank()) {
            throw new VaultAttestationException(
                    "shard.attestation.secret is required for container attestation.");
        }
        try {
            byte[] secret = Base64.getDecoder().decode(shardAttestationSecret);
            if (secret.length < 32) {
                throw new VaultAttestationException("shard.attestation.secret must decode to at least 32 bytes.");
            }
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret, "HmacSHA256"));
                byte[] digest = mac.doFinal(attestationMessage(nodeId, publicKeyBase64)
                        .getBytes(StandardCharsets.UTF_8));
                return "v1:" + Base64.getEncoder().encodeToString(digest);
            } finally {
                java.util.Arrays.fill(secret, (byte) 0);
            }
        } catch (Exception e) {
            if (e instanceof VaultAttestationException vaultException) {
                throw vaultException;
            }
            throw new VaultAttestationException("Failed to obtain container attestation quote: " + e.getMessage(), e);
        }
    }

    private String attestationMessage(String nodeId, String publicKeyBase64) {
        return "shard-attest:v1:" + nodeId + ":" + publicKeyBase64;
    }
}
