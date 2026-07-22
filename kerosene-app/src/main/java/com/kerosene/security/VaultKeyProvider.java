package source.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Facade for consumers that need the RAM-resident AES master key.
 *
 * Bootstrap, retry, Vault transport and JSON parsing live in dedicated
 * collaborators. This class keeps the existing injection contract stable for
 * encryption services, health checks and panic flows.
 */
@Component
public class VaultKeyProvider {

    private final MasterKeyMemoryStore masterKeyMemoryStore;

    public VaultKeyProvider(MasterKeyMemoryStore masterKeyMemoryStore) {
        this.masterKeyMemoryStore = masterKeyMemoryStore;
    }

    public SecretKey getMasterKey() {
        return masterKeyMemoryStore.getMasterKey();
    }

    public boolean isReady() {
        return masterKeyMemoryStore.isReady();
    }

    public void destroyMasterKey() {
        masterKeyMemoryStore.destroyMasterKey();
    }

    /**
     * Compatibility alias for older code that referenced the nested exception.
     * New Vault adapters throw {@link source.security.VaultAttestationException}.
     */
    @Deprecated(forRemoval = false)
    public static class VaultAttestationException extends source.security.VaultAttestationException {
        public VaultAttestationException(String message) {
            super(message);
        }
    }
}
