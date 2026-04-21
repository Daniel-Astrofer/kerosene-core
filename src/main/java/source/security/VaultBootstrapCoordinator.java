package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates Vault key bootstrap through Spring-managed lifecycle and
 * scheduling.
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
    private final TaskScheduler taskScheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> scheduledAttempt = new AtomicReference<>();

    private int attempt = 1;
    private long backoffMs = INITIAL_BACKOFF_MS;

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    @Value("${api.secret.aes.secret:}")
    private String devAesSecretBase64;

    public VaultBootstrapCoordinator(
            VaultEndpointResolver endpointResolver,
            VaultAttestationClient attestationClient,
            VaultProvisioningClient provisioningClient,
            MasterKeyMemoryStore masterKeyMemoryStore,
            @Qualifier("vaultBootstrapTaskScheduler") TaskScheduler taskScheduler) {
        this.endpointResolver = endpointResolver;
        this.attestationClient = attestationClient;
        this.provisioningClient = provisioningClient;
        this.masterKeyMemoryStore = masterKeyMemoryStore;
        this.taskScheduler = taskScheduler;
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

        try {
            if (vaultEnabled) {
                logger.info("[VaultBootstrapCoordinator] Vault mode active. Scheduling TPM attestation bootstrap.");
                scheduleAttempt(Duration.ZERO);
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
        ScheduledFuture<?> future = scheduledAttempt.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
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

    private void provisionOnce() {
        if (!running.get() || masterKeyMemoryStore.isReady()) {
            return;
        }

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
            logger.error("[VaultBootstrapCoordinator STALL] Vault rejected attestation on attempt {}: {}. "
                            + "Node remains in STALL mode. Retrying in {}ms...",
                    currentAttempt, e.getMessage(), currentBackoffMs);
        } catch (IOException e) {
            logger.warn("[VaultBootstrapCoordinator STALL] Network error on attempt {}: {}. Retrying in {}ms...",
                    currentAttempt, e.getMessage(), currentBackoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[VaultBootstrapCoordinator] Vault bootstrap task interrupted.");
            return;
        } catch (Exception e) {
            logger.error("[VaultBootstrapCoordinator CRITICAL] Unexpected error on attempt {}: {}",
                    currentAttempt, e.getMessage(), e);
        }

        scheduleNextAttempt(currentBackoffMs);
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

    private void scheduleAttempt(Duration delay) {
        if (!running.get() || masterKeyMemoryStore.isReady()) {
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(this::provisionOnce, Instant.now().plus(delay));
        ScheduledFuture<?> previous = scheduledAttempt.getAndSet(future);
        if (previous != null && !previous.isDone()) {
            previous.cancel(false);
        }
    }

    private void scheduleNextAttempt(long delayMs) {
        if (!running.get() || masterKeyMemoryStore.isReady()) {
            return;
        }

        synchronized (this) {
            attempt++;
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
        scheduleAttempt(Duration.ofMillis(delayMs));
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
