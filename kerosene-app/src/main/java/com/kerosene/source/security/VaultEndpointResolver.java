package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the active Vault endpoint from a static URL or a Tor hostname file.
 */
@Component
public class VaultEndpointResolver {

    private static final Logger logger = LoggerFactory.getLogger(VaultEndpointResolver.class);

    @Value("${vault.url:}")
    private String vaultUrl;

    @Value("${vault.onion.file:}")
    private String vaultUrlFile;

    public String resolveVaultUrl() {
        if (vaultUrl != null && !vaultUrl.isBlank()) {
            return vaultUrl;
        }

        if (vaultUrlFile == null || vaultUrlFile.isBlank()) {
            logger.warn("[VaultEndpointResolver] vault.onion.file property is blank. Node cannot discover Vault.");
            return null;
        }

        try {
            Path hostnameFile = Path.of(vaultUrlFile);
            if (!Files.exists(hostnameFile)) {
                logger.info("[VaultEndpointResolver] Vault hostname file does not exist at: {}.", vaultUrlFile);
                return null;
            }

            String onionHost = Files.readString(hostnameFile).trim();
            if (onionHost.isBlank()) {
                logger.warn("[VaultEndpointResolver] Vault hostname file exists but is empty at: {}", vaultUrlFile);
                return null;
            }
            if (!isValidOnionHost(onionHost)) {
                logger.error("[VaultEndpointResolver] Vault hostname file contains an invalid onion hostname.");
                return null;
            }

            String resolved = "http://" + onionHost;
            logger.info("[VaultEndpointResolver] Vault .onion auto-discovered: {}", resolved);
            return resolved;
        } catch (IOException e) {
            logger.error("[VaultEndpointResolver] Failed to read vault hostname file {}: {}",
                    vaultUrlFile, e.getMessage());
            return null;
        }
    }

    public String configuredVaultUrl() {
        return vaultUrl;
    }

    public String configuredVaultUrlFile() {
        return vaultUrlFile;
    }

    private boolean isValidOnionHost(String onionHost) {
        try {
            URI uri = URI.create("http://" + onionHost.trim());
            String host = uri.getHost();
            String rawPath = uri.getRawPath();
            return host != null
                    && host.endsWith(".onion")
                    && (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
