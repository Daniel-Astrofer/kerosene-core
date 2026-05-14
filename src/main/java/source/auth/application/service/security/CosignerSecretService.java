package source.auth.application.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.auth.application.service.cripto.encrypter.AES256;
import source.security.VaultKeyProvider;

import java.util.Arrays;
import java.util.Base64;

/**
 * Handles encryption and decryption of the platform's co-signer secret
 * and all AES-protected data (balance fields, TOTP secrets, etc).
 *
 * <h2>Key Management</h2>
 * <p>
 * The master key is NO LONGER read from @Value / environment variables.
 * It is fetched exclusively from {@link VaultKeyProvider}, which:
 * <ol>
 * <li>Performs TPM PCR attestation at boot time.</li>
 * <li>Sends the signed Quote to the central Key Server (Vault).</li>
 * <li>Receives the AES-256 master key ONLY in RAM — never on disk.</li>
 * </ol>
 *
 * <h2>Cipher</h2>
 * <p>
 * Delegates to {@link AES256} (AES-256-GCM, random 12-byte IV prepended to
 * the ciphertext). Output is Base64-encoded for safe DB storage as TEXT.
 *
 * <h2>PII / Secret Safety</h2>
 * <ul>
 * <li>Plaintext secrets are NEVER logged.</li>
 * <li>Generated secrets are zeroed from memory immediately after
 * encryption.</li>
 * <li>Startup fails fast if the Vault key is unavailable — no insecure
 * fallback.</li>
 * </ul>
 */
@Service
public class CosignerSecretService {

    private static final Logger log = LoggerFactory.getLogger(CosignerSecretService.class);

    private final AES256 aes;
    private final VaultKeyProvider vaultKeyProvider;

    /**
     * Constructor injection only — no @Value, no disk reads.
     * The masterKey lives in VaultKeyProvider's RAM.
     */
    public CosignerSecretService(AES256 aes, VaultKeyProvider vaultKeyProvider) {
        this.aes = aes;
        this.vaultKeyProvider = vaultKeyProvider;
        log.info("[CosignerSecretService] Initialized. Master key source: VaultKeyProvider (RAM-only).");
    }

    /**
     * Encrypts raw bytes with AES-256-GCM using the RAM-resident master key.
     *
     * @param secretBytes raw secret bytes (never logged)
     * @return Base64-encoded AES-GCM ciphertext (IV + ciphertext), safe for DB
     */
    public String encrypt(byte[] secretBytes) {
        try {
            byte[] cipherText = aes.encrypt(secretBytes, vaultKeyProvider.getMasterKey());
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            throw new CosignerEncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES-GCM ciphertext back to raw bytes.
     *
     * @param encryptedB64 Base64 ciphertext from DB
     * @return decrypted raw bytes — CALLER MUST zero this array after use
     */
    public byte[] decrypt(String encryptedB64) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(encryptedB64);
            return aes.decrypt(cipherBytes, vaultKeyProvider.getMasterKey());
        } catch (Exception e) {
            throw new CosignerEncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Generates a cryptographically secure random 32-byte secret, encrypts it,
     * and zeros the plaintext from memory before returning.
     *
     * @return Base64-encoded AES-GCM ciphertext, ready for DB storage
     */
    public String generateAndEncrypt() {
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        try {
            return encrypt(secret);
        } finally {
            Arrays.fill(secret, (byte) 0); // Zero plaintext from memory
        }
    }

    /** Typed exception for encryption/decryption failures. */
    public static class CosignerEncryptionException extends RuntimeException {
        public CosignerEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Returns a defensive copy of the master AES key bytes for use in HMAC
     * computations (e.g., StringCryptoConverter integrity tags).
     * The caller MUST zero the returned array after use.
     */
    public byte[] getMasterKeyBytes() {
        return vaultKeyProvider.getMasterKey().getEncoded();
    }
}
