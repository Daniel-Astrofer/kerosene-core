package source.auth.application.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.auth.application.service.cripto.encrypter.AES256;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Handles encryption and decryption of the platform's co-signer secret.
 *
 * <h2>Key</h2>
 * <p>
 * Reuses the same AES-256 master key already configured for the application
 * ({@code api.secret.aes.secret}), loaded as a Base64-encoded 256-bit value.
 * No separate environment variable is required.
 *
 * <h2>Cipher</h2>
 * <p>
 * Delegates to {@link AES256} (AES-256-GCM, random 12-byte IV prepended to
 * the ciphertext). Output is Base64-encoded for safe DB storage as TEXT.
 *
 * <h2>PII / Secret safety</h2>
 * <ul>
 * <li>Plaintext secrets are never logged.</li>
 * <li>Generated secrets are zeroed out of memory immediately after
 * encryption.</li>
 * <li>Startup fails fast ({@link IllegalStateException}) if the key is missing
 * or has incorrect length — no insecure fallback.</li>
 * </ul>
 */
@Service
public class CosignerSecretService {

    private static final Logger log = LoggerFactory.getLogger(CosignerSecretService.class);
    private static final int REQUIRED_KEY_BYTES = 32; // 256-bit

    private final AES256 aes;
    private SecretKey masterKey;

    /** Shared AES key, same one used by RedisService and TOTPValidator. */
    @Value("${api.secret.aes.secret}")
    private String aesSecretBase64;

    public CosignerSecretService(AES256 aes) {
        this.aes = aes;
    }

    /**
     * Validates and loads the master key at startup.
     * Throws {@link IllegalStateException} if the key is absent or wrong length.
     */
    @PostConstruct
    void init() {
        if (aesSecretBase64 == null || aesSecretBase64.isBlank()) {
            throw new IllegalStateException(
                    "[Security] api.secret.aes.secret is not configured. " +
                            "Please set the AES_SECRET environment variable.");
        }
        byte[] keyBytes = Base64.getDecoder().decode(aesSecretBase64);
        if (keyBytes.length != REQUIRED_KEY_BYTES) {
            throw new IllegalStateException(
                    "[Security] api.secret.aes.secret must decode to exactly 32 bytes (256-bit). " +
                            "Got " + keyBytes.length + " bytes.");
        }
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
        log.info("[CosignerSecretService] Co-signer encryption ready ({} bytes key).", REQUIRED_KEY_BYTES);
    }

    /**
     * Encrypts a raw secret with AES-256-GCM.
     *
     * @param secretBytes raw secret bytes (never logged)
     * @return Base64-encoded AES-GCM ciphertext (IV + ciphertext), safe for DB
     *         storage
     */
    public String encrypt(byte[] secretBytes) {
        try {
            byte[] cipherText = aes.encrypt(secretBytes, masterKey);
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            throw new CosignerEncryptionException("Failed to encrypt co-signer secret", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES-GCM ciphertext back to raw bytes.
     *
     * @param encryptedB64 the value stored in {@code platform_cosigner_secret}
     * @return decrypted raw bytes (zero out after use)
     */
    public byte[] decrypt(String encryptedB64) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(encryptedB64);
            return aes.decrypt(cipherBytes, masterKey);
        } catch (Exception e) {
            throw new CosignerEncryptionException("Failed to decrypt co-signer secret", e);
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
}
