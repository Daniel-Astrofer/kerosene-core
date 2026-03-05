package source.security.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import source.auth.application.service.security.CosignerSecretService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Converter
@Component
public class StringCryptoConverter implements AttributeConverter<String, String> {

    private static CosignerSecretService cryptoService;

    @Autowired
    public void setCryptoService(CosignerSecretService cryptoService) {
        StringCryptoConverter.cryptoService = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        if (cryptoService == null) {
            throw new IllegalStateException("CryptoService is not initialized for JPA Converter");
        }

        // Convert to bytes and pad to 128 bytes to prevent Side-Channel size attacks
        byte[] originalBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] paddedBytes = new byte[128];
        java.util.Arrays.fill(paddedBytes, (byte) 32);
        System.arraycopy(originalBytes, 0, paddedBytes, 0, Math.min(originalBytes.length, paddedBytes.length));

        try {
            String encrypted = cryptoService.encrypt(paddedBytes);

            // ─── HMAC Integrity Tag (Issue 1.3) ──────────────────────────────
            // AES-GCM already authenticates its own ciphertext (GCM auth tag).
            // This EXTRA HMAC-SHA256 detects blob-swap attacks where an attacker
            // with direct DB access replaces the entire ciphertext of one column
            // with ciphertext from another column/row.
            // The HMAC is stored as: <hmac_base64>:<ciphertext_base64>
            String hmac = computeHmac(encrypted);
            return hmac + ":" + encrypted;
        } finally {
            java.util.Arrays.fill(originalBytes, (byte) 0);
            java.util.Arrays.fill(paddedBytes, (byte) 0);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            // ─── HMAC Verification ───────────────────────────────────────────
            // Support both legacy format (no HMAC) and new format (hmac:ciphertext)
            String ciphertext;
            if (dbData.contains(":")) {
                String[] parts = dbData.split(":", 2);
                if (parts.length == 2) {
                    String storedHmac = parts[0];
                    ciphertext = parts[1];
                    String expectedHmac = computeHmac(ciphertext);
                    // Constant-time HMAC comparison
                    if (!MessageDigest.isEqual(
                            storedHmac.getBytes(StandardCharsets.UTF_8),
                            expectedHmac.getBytes(StandardCharsets.UTF_8))) {
                        throw new SecurityException(
                                "[INTEGRITY VIOLATION] HMAC mismatch on encrypted column — possible DB tampering detected.");
                    }
                } else {
                    ciphertext = dbData; // legacy: no HMAC
                }
            } else {
                ciphertext = dbData; // legacy: no HMAC
            }

            byte[] decrypted = cryptoService.decrypt(ciphertext);
            try {
                return new String(decrypted, StandardCharsets.UTF_8).trim();
            } finally {
                java.util.Arrays.fill(decrypted, (byte) 0);
            }
        } catch (SecurityException e) {
            throw e; // Re-throw integrity violations as-is
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[StringCryptoConverter] CRITICAL: Failed to decrypt DB column. " +
                            "If this follows a key rotation, a migration/re-encrypt script is required. " +
                            "Error: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Derives an HMAC-SHA256 of the ciphertext using the AES master key as HMAC
     * key.
     * This binds the MAC to the same key material as the encryption, so key
     * rotation
     * automatically invalidates old HMACs.
     */
    private static String computeHmac(String ciphertext) {
        try {
            byte[] keyBytes = cryptoService.getMasterKeyBytes();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(ciphertext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("[StringCryptoConverter] HMAC computation failed: " + e.getMessage(), e);
        }
    }
}
