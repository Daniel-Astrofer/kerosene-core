package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Value("${vault.onion.file:}")
    private String vaultUrlFile;

    @Value("${vault.proxy.host:}")
    private String proxyHost;

    @Value("${vault.proxy.port:0}")
    private int proxyPort;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    @Value("${sovereignty.heartbeat.initial-grace-ms:15000}")
    private long heartbeatInitialGraceMs;

    @Value("${sovereignty.heartbeat.request-timeout-ms:5000}")
    private int heartbeatRequestTimeoutMs;

    @Value("${sovereignty.heartbeat.retry-attempts:2}")
    private int heartbeatRetryAttempts;

    @Value("${sovereignty.heartbeat.retry-backoff-ms:250}")
    private long heartbeatRetryBackoffMs;

    @Value("${sovereignty.heartbeat.warn-after-consecutive-failures:3}")
    private int heartbeatWarnAfterConsecutiveFailures;

    private String nodeId;
    private final ShardIdentityManager shardIdentityManager;
    private final VaultKeyProvider vaultKeyProvider;
    private final TelemetryService telemetryService;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long heartbeatsEnabledAtMs = -1;

    public SovereigntyHeartbeatService(ShardIdentityManager shardIdentityManager,
            VaultKeyProvider vaultKeyProvider,
            TelemetryService telemetryService) {
        this.shardIdentityManager = shardIdentityManager;
        this.vaultKeyProvider = vaultKeyProvider;
        this.telemetryService = telemetryService;
    }

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
        if (!vaultKeyProvider.isReady()) {
            heartbeatsEnabledAtMs = -1;
            consecutiveFailures.set(0);
            logger.debug("[Heartbeat] Waiting for Vault provisioning before sending heartbeats.");
            return;
        }

        try {
            long now = System.currentTimeMillis();
            if (heartbeatsEnabledAtMs < 0) {
                heartbeatsEnabledAtMs = now;
                logger.info("[Heartbeat] Vault provisioning complete. Delaying heartbeats for {}ms to let Tor circuits stabilize.",
                        heartbeatInitialGraceMs);
                return;
            }
            if ((now - heartbeatsEnabledAtMs) < heartbeatInitialGraceMs) {
                logger.debug("[Heartbeat] In Tor warm-up window after provisioning, skipping.");
                return;
            }

            String resolvedUrl = resolveVaultUrl();
            if (resolvedUrl == null || resolvedUrl.isBlank()) {
                logger.debug("[Heartbeat] Vault URL not yet available, skipping.");
                return;
            }

            long timestamp = System.currentTimeMillis();
            String signature = shardIdentityManager.sign("heartbeat:" + timestamp);
            java.util.Map<String, String> heartbeatHeaders = java.util.Map.of(
                        "X-Node-Id", nodeId,
                        "X-Shard-Timestamp", Long.toString(timestamp),
                        "X-Shard-Signature", signature);

            sendHeartbeatWithRetry(resolvedUrl, heartbeatHeaders);

            int recoveredAfter = consecutiveFailures.getAndSet(0);
            if (recoveredAfter > 0) {
                logger.info("[Heartbeat] Recovered Vault connectivity after {} consecutive failures.", recoveredAfter);
            }
        } catch (Exception e) {
            telemetryService.recordHeartbeatFailure("vault-heartbeat");
            int failures = consecutiveFailures.incrementAndGet();

            if (failures >= Math.max(1, heartbeatWarnAfterConsecutiveFailures)) {
                if (failures == heartbeatWarnAfterConsecutiveFailures || failures % heartbeatWarnAfterConsecutiveFailures == 0) {
                    logger.warn("[Heartbeat] Failed to reach Vault via Tor ({} consecutive failures): {}",
                            failures, e.getMessage());
                } else {
                    logger.debug("[Heartbeat] Vault still unreachable after {} consecutive failures: {}",
                            failures, e.getMessage());
                }
            } else {
                logger.debug("[Heartbeat] Transient Tor failure contacting Vault ({} of {} before WARN): {}",
                        failures, heartbeatWarnAfterConsecutiveFailures, e.getMessage());
            }
        }
    }

    private void sendHeartbeatWithRetry(String resolvedUrl, java.util.Map<String, String> heartbeatHeaders)
            throws Exception {
        Exception lastFailure = null;
        int maxAttempts = Math.max(1, heartbeatRetryAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                sendHeartbeatOnce(resolvedUrl, heartbeatHeaders);
                return;
            } catch (IOException e) {
                lastFailure = e;
                if (attempt >= maxAttempts) {
                    throw e;
                }
                logger.debug("[Heartbeat] Retrying heartbeat after transient Tor error (attempt {}/{}): {}",
                        attempt, maxAttempts, e.getMessage());
                sleepBeforeRetry();
            }
        }

        throw lastFailure;
    }

    private void sendHeartbeatOnce(String resolvedUrl, java.util.Map<String, String> heartbeatHeaders) throws Exception {
        if (proxyPath == null || proxyPath.isBlank()) {
            throw new IllegalStateException(
                    "Vault heartbeat requires Tor UDS routing. Configure vault.proxy.path=/var/run/tor/socks/tor.sock");
        }

        UdsSocks5Transport transport = new UdsSocks5Transport(proxyPath, heartbeatRequestTimeoutMs);
        UdsSocks5Transport.HttpResult response = transport.executeHttpRequest(
                resolvedUrl + "/v1/vault/heartbeat",
                "POST",
                "",
                heartbeatHeaders);

        if (response.statusCode() != 200) {
            logger.warn("[Heartbeat] Vault rejected heartbeat: HTTP {}", response.statusCode());
        } else {
            logger.debug("[Heartbeat] ACK from Vault.");
        }
    }

    private void sleepBeforeRetry() throws InterruptedException {
        if (heartbeatRetryBackoffMs <= 0) {
            return;
        }
        Thread.sleep(heartbeatRetryBackoffMs);
    }

    private void initClient() {
        this.nodeId = getNodeIdentity();
        if (proxyPath == null || proxyPath.isBlank()) {
            throw new IllegalStateException(
                    "Vault heartbeat requires Tor UDS routing. Configure vault.proxy.path=/var/run/tor/socks/tor.sock");
        }
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
        return shardIdentityManager.getStableNodeId();
    }
}
