package source.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import source.common.security.StringColumnCryptoPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Converter
@Component
public class StringCryptoConverter implements AttributeConverter<String, String> {

    private static StringColumnCryptoPort cryptoPort;

    @Autowired
    public void setCryptoPort(StringColumnCryptoPort cryptoPort) {
        StringCryptoConverter.cryptoPort = cryptoPort;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        if (cryptoPort == null) {
            throw new IllegalStateException("String column crypto port is not initialized for JPA Converter");
        }

        byte[] originalBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] paddedBytes = new byte[128];
        java.util.Arrays.fill(paddedBytes, (byte) 32);
        System.arraycopy(originalBytes, 0, paddedBytes, 0, Math.min(originalBytes.length, paddedBytes.length));

        try {
            String encrypted = cryptoPort.encrypt(paddedBytes);
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
        if (cryptoPort == null) {
            throw new IllegalStateException("String column crypto port is not initialized for JPA Converter");
        }
        try {
            String ciphertext;
            if (dbData.contains(":")) {
                String[] parts = dbData.split(":", 2);
                if (parts.length == 2) {
                    String storedHmac = parts[0];
                    ciphertext = parts[1];
                    String expectedHmac = computeHmac(ciphertext);
                    if (!MessageDigest.isEqual(
                            storedHmac.getBytes(StandardCharsets.UTF_8),
                            expectedHmac.getBytes(StandardCharsets.UTF_8))) {
                        throw new SecurityException(
                                "[INTEGRITY VIOLATION] HMAC mismatch on encrypted column — possible DB tampering detected.");
                    }
                } else {
                    ciphertext = dbData;
                }
            } else {
                ciphertext = dbData;
            }

            byte[] decrypted = cryptoPort.decrypt(ciphertext);
            try {
                return new String(decrypted, StandardCharsets.UTF_8).trim();
            } finally {
                java.util.Arrays.fill(decrypted, (byte) 0);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[StringCryptoConverter] CRITICAL: Failed to decrypt DB column. "
                            + "If this follows a key rotation, a migration/re-encrypt script is required. "
                            + "Error: " + e.getMessage(),
                    e);
        }
    }

    private static String computeHmac(String ciphertext) {
        byte[] keyBytes = null;
        try {
            keyBytes = cryptoPort.getMasterKeyBytes();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(ciphertext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("[StringCryptoConverter] HMAC computation failed: " + e.getMessage(), e);
        } finally {
            if (keyBytes != null) {
                java.util.Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }
}
