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
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

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
        String publicKeyBase64 = shardIdentityManager.getPublicKeyBase64();

        AttestationChallenge challenge;
        if (isOnionUrl(resolvedVaultUrl)) {
            requireTorTransport();
            challenge = fetchAttestationChallengeViaUds(resolvedVaultUrl, nodeId);
        } else {
            challenge = fetchAttestationChallengeDirect(resolvedVaultUrl, nodeId);
        }

        String attestationSignature = signAttestationChallenge(nodeId, publicKeyBase64, challenge);
        logger.info("[VaultAttestationClient] Challenge-bound attestation prepared. Node: {}. Attesting...", nodeId);

        String attestBody = objectMapper.writeValueAsString(Map.of(
                "tpm_quote", "v2:" + attestationSignature,
                "node_id", nodeId,
                "public_key", publicKeyBase64,
                "challenge_id", challenge.challengeId(),
                "challenge_nonce", challenge.challengeNonce(),
                "attestation_signature", attestationSignature));

        String sessionToken;
        if (isOnionUrl(resolvedVaultUrl)) {
            sessionToken = attestViaUds(resolvedVaultUrl, nodeId, attestBody);
        } else {
            sessionToken = attestViaDirect(resolvedVaultUrl, nodeId, attestBody);
        }

        return new VaultAttestationSession(nodeId, sessionToken.trim());
    }

    private boolean isOnionUrl(String url) {
        return url != null && url.contains(".onion");
    }

    private AttestationChallenge fetchAttestationChallengeDirect(String resolvedVaultUrl, String nodeId)
            throws IOException, InterruptedException {
        URI uri = URI.create(resolvedVaultUrl + "/v1/vault/challenge");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("X-Node-Id", nodeId)
                .timeout(Duration.ofMillis(vaultRequestTimeoutMs))
                .build();

        HttpResponse<String> response = directClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new VaultAttestationException("Vault challenge request failed (direct): " + response.body());
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());
            String challengeId = text(body, "challenge_id", "challengeId", "id");
            String challengeNonce = text(body, "challenge_nonce", "challengeNonce", "challenge", "nonce");
            if (challengeId == null || challengeNonce == null) {
                throw new VaultAttestationException("Vault challenge response is missing challenge_id or challenge_nonce.");
            }
            return new AttestationChallenge(challengeId, challengeNonce);
        } catch (IOException exception) {
            throw new VaultAttestationException("Failed to parse Vault challenge response: " + exception.getMessage(),
                    exception);
        }
    }

    private String attestViaDirect(String resolvedVaultUrl, String nodeId, String attestBody) throws IOException {
        logger.debug("[VaultAttestationClient] Routing attestation via direct HTTP to: {}", resolvedVaultUrl);
        URI uri = URI.create(resolvedVaultUrl + "/v1/vault/attest");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(attestBody))
                .header("Content-Type", "application/json")
                .header("X-Node-Id", nodeId)
                .timeout(Duration.ofMillis(vaultRequestTimeoutMs))
                .build();

        try {
            HttpResponse<String> response = directClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new VaultAttestationException("Vault rejected attestation (direct): " + response.body());
            }
            logger.info("[VaultAttestationClient] Hardware attested via direct HTTP. Session token received.");
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("[VaultAttestationClient] Attestation interrupted", e);
        }
    }

    private AttestationChallenge fetchAttestationChallengeViaUds(String resolvedVaultUrl, String nodeId)
            throws IOException, InterruptedException {
        UdsSocks5Transport transport = new UdsSocks5Transport(proxyPath, vaultRequestTimeoutMs);
        Map<String, String> headers = Map.of("X-Node-Id", nodeId);
        UdsSocks5Transport.HttpResult result = transport.executeHttpRequest(
                resolvedVaultUrl + "/v1/vault/challenge",
                "GET",
                null,
                headers);

        if (result.statusCode() != 200) {
            throw new VaultAttestationException("Vault challenge request failed (UDS): " + result.bodyAsString());
        }

        try {
            JsonNode body = objectMapper.readTree(result.body());
            String challengeId = text(body, "challenge_id", "challengeId", "id");
            String challengeNonce = text(body, "challenge_nonce", "challengeNonce", "challenge", "nonce");
            if (challengeId == null || challengeNonce == null) {
                throw new VaultAttestationException("Vault challenge response is missing challenge_id or challenge_nonce.");
            }
            return new AttestationChallenge(challengeId, challengeNonce);
        } catch (IOException exception) {
            throw new VaultAttestationException("Failed to parse Vault challenge response: " + exception.getMessage(),
                    exception);
        }
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

    private void requireTorTransport() {
        if (proxyPath == null || proxyPath.isBlank()) {
            throw new VaultAttestationException(
                    "Vault attestation requires Tor UDS routing. Configure vault.proxy.path=/var/run/tor/socks/tor.sock");
        }
    }

    private String signAttestationChallenge(
            String nodeId,
            String publicKeyBase64,
            AttestationChallenge challenge) {
        try {
            return shardIdentityManager.sign(attestationMessage(nodeId, publicKeyBase64, challenge));
        } catch (RuntimeException exception) {
            throw new VaultAttestationException(
                    "Failed to sign Vault attestation challenge: " + exception.getMessage(),
                    exception);
        }
    }

    private String attestationMessage(String nodeId, String publicKeyBase64, AttestationChallenge challenge) {
        return "vault-attest:v2\n"
                + "node_id=" + nodeId + "\n"
                + "public_key=" + publicKeyBase64 + "\n"
                + "challenge_id=" + challenge.challengeId() + "\n"
                + "challenge_nonce=" + challenge.challengeNonce();
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private record AttestationChallenge(String challengeId, String challengeNonce) {
    }
}
