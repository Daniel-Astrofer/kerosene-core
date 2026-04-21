package source.security;

/**
 * Raised when Vault rejects attestation or returns invalid key material.
 */
public class VaultAttestationException extends RuntimeException {

    public VaultAttestationException(String message) {
        super(message);
    }

    public VaultAttestationException(String message, Throwable cause) {
        super(message, cause);
    }
}
