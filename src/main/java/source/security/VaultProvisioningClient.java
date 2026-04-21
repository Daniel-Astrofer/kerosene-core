package source.security;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches and decodes the AES master key after Vault attestation succeeds.
 */
@Component
public class VaultProvisioningClient {

    private static final Logger logger = LoggerFactory.getLogger(VaultProvisioningClient.class);

    private final JsonFactory jsonFactory = new JsonFactory();
    private final HttpClient directClient;

    @Value("${vault.proxy.host:}")
    private String proxyHost;

    @Value("${vault.proxy.port:0}")
    private int proxyPort;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    @Value("${vault.request-timeout-ms:75000}")
    private int vaultRequestTimeoutMs;

    public VaultProvisioningClient() {
        this.directClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public byte[] provisionMasterKey(String resolvedVaultUrl, VaultAttestationSession session)
            throws IOException, InterruptedException {
        byte[] keyBytes;
        if (proxyPath != null && !proxyPath.isBlank()) {
            keyBytes = provisionViaUds(resolvedVaultUrl, session);
        } else {
            keyBytes = provisionDirectly(resolvedVaultUrl, session);
        }

        if (keyBytes == null || keyBytes.length != MasterKeyMemoryStore.KEY_BYTES) {
            throw new VaultAttestationException(
                    "Invalid key length from Vault: " + (keyBytes == null ? "null" : keyBytes.length));
        }
        return keyBytes;
    }

    private byte[] provisionViaUds(String resolvedVaultUrl, VaultAttestationSession session) throws IOException {
        UdsSocks5Transport transport = new UdsSocks5Transport(proxyPath, vaultRequestTimeoutMs);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + session.sessionToken());
        headers.put("X-Node-Id", session.nodeId());

        UdsSocks5Transport.HttpResult result = transport.executeHttpRequest(
                resolvedVaultUrl + "/v1/vault/provision",
                "GET",
                null,
                headers);

        if (result.statusCode() != 200) {
            throw new VaultAttestationException("Provisioning failed via UDS: Status " + result.statusCode());
        }

        return extractKeyBytesFromVaultResponse(result.body());
    }

    private byte[] provisionDirectly(String resolvedVaultUrl, VaultAttestationSession session)
            throws IOException, InterruptedException {
        assertDirectConnectionAllowed();
        logger.warn("[VaultProvisioningClient] DEV MODE - connecting to Vault directly without Tor.");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolvedVaultUrl + "/v1/vault/provision"))
                .header("Authorization", "Bearer " + session.sessionToken())
                .header("X-Node-Id", session.nodeId())
                .timeout(Duration.ofMillis(vaultRequestTimeoutMs))
                .GET()
                .build();

        HttpResponse<byte[]> response = directClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new VaultAttestationException("Provisioning failed: Status " + response.statusCode());
        }

        return extractKeyBytesFromVaultResponse(response.body());
    }

    private void assertDirectConnectionAllowed() {
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            logger.error("[VaultProvisioningClient] CRITICAL: vault.proxy.host={} is set but vault.proxy.path is not.",
                    proxyHost);
            logger.error("[VaultProvisioningClient] Java ProxySelector would resolve .onion via local DNS.");
            logger.error("[VaultProvisioningClient] Set vault.proxy.path to the Tor UDS socket path instead.");
            throw new VaultAttestationException(
                    "DNS leak prevention: vault.proxy.path (UDS socket) is required when connecting to .onion URLs. "
                            + "Configure vault.proxy.path=/var/run/tor/socks/tor.sock");
        }
    }

    private byte[] extractKeyBytesFromVaultResponse(byte[] responseBody) {
        try (JsonParser parser = jsonFactory.createParser(new ByteArrayInputStream(responseBody))) {
            while (parser.nextToken() != null) {
                if (JsonToken.FIELD_NAME.equals(parser.currentToken())
                        && "aes_key".equals(parser.currentName())) {
                    parser.nextToken();
                    return parser.getBinaryValue(Base64Variants.getDefaultVariant());
                }
            }
        } catch (IOException e) {
            throw new VaultAttestationException("Failed to parse Vault response: " + e.getMessage(), e);
        }

        throw new VaultAttestationException("Vault response does not contain 'aes_key' field. Check Vault secret path.");
    }
}
