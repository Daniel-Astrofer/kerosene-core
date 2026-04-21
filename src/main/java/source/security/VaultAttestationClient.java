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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public VaultAttestationClient(ShardIdentityManager shardIdentityManager, ObjectMapper objectMapper) {
        this.shardIdentityManager = shardIdentityManager;
        this.objectMapper = objectMapper;
        this.directClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public VaultAttestationSession attest(String resolvedVaultUrl) throws IOException, InterruptedException {
        String nodeId = shardIdentityManager.getStableNodeId();
        String tpmQuote = obtainTpmPcrQuote();
        logger.info("[VaultAttestationClient] TPM Quote obtained. Node: {}. Attesting...", nodeId);

        String attestBody = objectMapper.writeValueAsString(Map.of(
                "tpm_quote", tpmQuote,
                "node_id", nodeId,
                "public_key", shardIdentityManager.getPublicKeyBase64()));

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

    private String obtainTpmPcrQuote() {
        try {
            byte[] nonce = new byte[32];
            new SecureRandom().nextBytes(nonce);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("TPM_PCR_STATE".getBytes(StandardCharsets.UTF_8));
            digest.update(nonce);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            throw new VaultAttestationException("Failed to obtain TPM PCR Quote: " + e.getMessage(), e);
        }
    }
}
