package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates Vault key bootstrap through Spring-managed lifecycle.
 */
@Component
public class VaultBootstrapCoordinator implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(VaultBootstrapCoordinator.class);
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final VaultEndpointResolver endpointResolver;
    private final VaultAttestationClient attestationClient;
    private final VaultProvisioningClient provisioningClient;
    private final MasterKeyMemoryStore masterKeyMemoryStore;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private int attempt = 1;
    private long backoffMs = INITIAL_BACKOFF_MS;

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    @Value("${api.secret.aes.secret:}")
    private String devAesSecretBase64;

    @Value("${vault.bootstrap.startup-timeout-ms:180000}")
    private long startupTimeoutMs;

    public VaultBootstrapCoordinator(
            VaultEndpointResolver endpointResolver,
            VaultAttestationClient attestationClient,
            VaultProvisioningClient provisioningClient,
            MasterKeyMemoryStore masterKeyMemoryStore) {
        this.endpointResolver = endpointResolver;
        this.attestationClient = attestationClient;
        this.provisioningClient = provisioningClient;
        this.masterKeyMemoryStore = masterKeyMemoryStore;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        logger.info("[VaultBootstrapCoordinator] Config: vault.enabled={}, vault.url='{}', vault.onion.file='{}', vault.proxy.path='{}'",
                vaultEnabled,
                endpointResolver.configuredVaultUrl(),
                endpointResolver.configuredVaultUrlFile(),
                proxyPath);

        synchronized (this) {
            attempt = 1;
            backoffMs = INITIAL_BACKOFF_MS;
        }

        try {
            if (vaultEnabled) {
                logger.info("[VaultBootstrapCoordinator] Vault mode active. Provisioning master key before application startup completes.");
                provisionDuringStartup();
            } else {
                logger.warn("[VaultBootstrapCoordinator] DEVELOPMENT MODE - key loaded from api.secret.aes.secret. "
                        + "Never use this in production. Set vault.enabled=true.");
                loadKeyFromEnvironment();
            }
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    private void provisionFromVault() throws IOException, InterruptedException {
        String resolvedVaultUrl = endpointResolver.resolveVaultUrl();
        if (resolvedVaultUrl == null || resolvedVaultUrl.isBlank()) {
            throw new IOException("Vault URL is not configured. Set vault.url or vault.onion.file.");
        }

        byte[] keyBytes = null;
        try {
            VaultAttestationSession session = attestationClient.attest(resolvedVaultUrl);
            keyBytes = provisioningClient.provisionMasterKey(resolvedVaultUrl, session);
            masterKeyMemoryStore.storeMasterKey(keyBytes);
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private void provisionDuringStartup() {
        long timeoutMs = Math.max(1L, startupTimeoutMs);
        Instant deadline = Instant.now().plusMillis(timeoutMs);

        while (running.get() && !masterKeyMemoryStore.isReady()) {
            int currentAttempt;
            long currentBackoffMs;
            synchronized (this) {
                currentAttempt = attempt;
                currentBackoffMs = backoffMs;
            }

            logger.info("[VaultBootstrapCoordinator] Attempt {} to fetch master key...", currentAttempt);
            try {
                provisionFromVault();
                logger.info("[VaultBootstrapCoordinator] SUCCESS: Master key provisioned on attempt {}.", currentAttempt);
                return;
            } catch (VaultAttestationException e) {
                logger.warn("[VaultBootstrapCoordinator] Vault attestation rejected on attempt {}: {}. Retrying in {}ms...",
                        currentAttempt, e.getMessage(), currentBackoffMs);
            } catch (IOException e) {
                logger.warn("[VaultBootstrapCoordinator] Network error on attempt {}: {}. Retrying in {}ms...",
                        currentAttempt, e.getMessage(), currentBackoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("[VaultBootstrapCoordinator] Vault bootstrap interrupted during startup.", e);
            } catch (Exception e) {
                logger.error("[VaultBootstrapCoordinator] Unexpected error on attempt {}: {}",
                        currentAttempt, e.getMessage(), e);
            }

            if (Instant.now().plusMillis(currentBackoffMs).isAfter(deadline)) {
                throw new IllegalStateException(
                        "[VaultBootstrapCoordinator] Unable to provision master key within "
                                + timeoutMs
                                + "ms. Failing startup instead of entering STALL mode.");
            }

            sleepBeforeRetry(currentBackoffMs);
            advanceBackoff();
        }
    }

    private void sleepBeforeRetry(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[VaultBootstrapCoordinator] Vault bootstrap interrupted during retry backoff.", e);
        }
    }

    private synchronized void advanceBackoff() {
        attempt++;
        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
    }

    private void loadKeyFromEnvironment() {
        if (devAesSecretBase64 == null || devAesSecretBase64.isBlank()) {
            throw new IllegalStateException(
                    "[VaultBootstrapCoordinator] api.secret.aes.secret is not set. "
                            + "Set AES_SECRET env var (dev) or configure vault.enabled=true (prod).");
        }

        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(devAesSecretBase64);
            if (keyBytes.length != MasterKeyMemoryStore.KEY_BYTES) {
                throw new IllegalStateException(
                        "[VaultBootstrapCoordinator] api.secret.aes.secret must decode to "
                                + MasterKeyMemoryStore.KEY_BYTES + " bytes. Got: " + keyBytes.length);
            }
            masterKeyMemoryStore.storeMasterKey(keyBytes);
            logger.info("[VaultBootstrapCoordinator] Dev key loaded ({} bytes).", MasterKeyMemoryStore.KEY_BYTES);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "[VaultBootstrapCoordinator] api.secret.aes.secret must be valid Base64.", e);
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }
}
