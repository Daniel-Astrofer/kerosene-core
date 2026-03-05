package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Push-based Heartbeat Service (Beaconing).
 * Constantemente envia sinais de vida (Heartbeat) para o Vault via Tor,
 * informando que esta Shard está ativa e intacta.
 *
 * Em producao, isso usaria mTLS para garantir que apenas Shards puras
 * conseguem enviar pings validos, prevenindo spoofing.
 */
@Service
public class SovereigntyHeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(SovereigntyHeartbeatService.class);

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${vault.url:}")
    private String vaultUrl;

    @Value("${vault.url.file:}")
    private String vaultUrlFile;

    @Value("${vault.proxy.host:}")
    private String proxyHost;

    @Value("${vault.proxy.port:0}")
    private int proxyPort;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    // For injecting the built client to reuse connections
    private HttpClient httpClient;
    private String nodeId;

    /** Initialise the HTTP client eagerly at startup to avoid race conditions. */
    @PostConstruct
    public void onStartup() {
        if (vaultEnabled) {
            try {
                initClient();
            } catch (Exception e) {
                logger.warn("[Heartbeat] Failed to initialise HTTP client at startup: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedRate = 5000) // A cada 5 segundos
    public void sendHeartbeat() {
        if (!vaultEnabled)
            return;

        try {
            String resolvedUrl = resolveVaultUrl();
            if (resolvedUrl == null || resolvedUrl.isBlank()) {
                logger.debug("[Heartbeat] Vault URL not yet available, skipping.");
                return;
            }

            if (proxyPath != null && !proxyPath.isBlank()) {
                // Caminho Produção/UDS SOCKS5
                UdsSocks5Transport transport = new UdsSocks5Transport(proxyPath);
                UdsSocks5Transport.HttpResult response = transport.executeHttpRequest(
                        resolvedUrl + "/v1/vault/heartbeat",
                        "POST",
                        "",
                        java.util.Map.of("X-Node-Id", nodeId));

                if (response.statusCode() != 200) {
                    logger.warn("[Heartbeat] Vault rejected heartbeat: HTTP {}", response.statusCode());
                } else {
                    logger.debug("[Heartbeat] ACK from Vault.");
                }
            } else {
                // Caminho Dev/TCP (HttpClient)
                if (httpClient == null)
                    return; // startup init failed, skip

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(resolvedUrl + "/v1/vault/heartbeat"))
                        .header("X-Node-Id", nodeId)
                        .timeout(Duration.ofSeconds(15)) // prevent Tor latency from blocking scheduler thread
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    logger.warn("[Heartbeat] Vault rejected heartbeat: HTTP {}", response.statusCode());
                } else {
                    logger.debug("[Heartbeat] ACK from Vault.");
                }
            }
        } catch (Exception e) {
            logger.warn("[Heartbeat] Failed to reach Vault via Tor: {}", e.getMessage());
        }
    }

    private void initClient() {
        this.nodeId = getNodeIdentity();

        if (proxyPath != null && !proxyPath.isBlank()) {
            // UdsSocks5Transport is stateless and used dynamically per request,
            // so we don't need to build an HttpClient here.
            return;
        }

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));

        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }

        String resolvedUrl = resolveVaultUrl();
        if (resolvedUrl != null && resolvedUrl.startsWith("https")) {
            javax.net.ssl.SSLParameters sslParams = new javax.net.ssl.SSLParameters();
            sslParams.setProtocols(new String[] { "TLSv1.3" });
            sslParams.setNeedClientAuth(true);
            clientBuilder.sslParameters(sslParams);
        }

        this.httpClient = clientBuilder.build();
    }

    /**
     * Resolves the Vault URL dynamically — same logic as VaultKeyProvider.
     */
    private String resolveVaultUrl() {
        if (vaultUrl != null && !vaultUrl.isBlank()) {
            return vaultUrl;
        }
        if (vaultUrlFile != null && !vaultUrlFile.isBlank()) {
            try {
                Path hostnameFile = Path.of(vaultUrlFile);
                if (Files.exists(hostnameFile)) {
                    String onionHost = Files.readString(hostnameFile).trim();
                    if (!onionHost.isBlank()) {
                        return "http://" + onionHost;
                    }
                }
            } catch (IOException e) {
                logger.warn("[Heartbeat] Failed to read vault hostname file: {}", e.getMessage());
            }
        }
        return null;
    }

    private String getNodeIdentity() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-node";
        }
    }
}
