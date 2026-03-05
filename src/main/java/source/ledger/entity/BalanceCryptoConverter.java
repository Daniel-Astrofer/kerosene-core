package source.ledger.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import source.auth.application.service.security.CosignerSecretService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Converter
@Component
public class BalanceCryptoConverter implements AttributeConverter<BigDecimal, String> {

    private static CosignerSecretService cryptoService;

    @Autowired
    public void setCryptoService(CosignerSecretService cryptoService) {
        BalanceCryptoConverter.cryptoService = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        if (cryptoService == null) {
            throw new IllegalStateException("CryptoService is not initialized for JPA Converter");
        }
        // Extract string, pad to 64 bytes to prevent Side-Channel size attacks
        String plainStr = attribute.toPlainString();
        byte[] originalBytes = plainStr.getBytes(StandardCharsets.UTF_8);
        byte[] paddedBytes = new byte[64];

        // Pad with space (0x20)
        java.util.Arrays.fill(paddedBytes, (byte) 32);
        System.arraycopy(originalBytes, 0, paddedBytes, 0, originalBytes.length);

        try {
            return cryptoService.encrypt(paddedBytes);
        } finally {
            // Zero out memory to block Cold Boot Attacks
            java.util.Arrays.fill(originalBytes, (byte) 0);
            java.util.Arrays.fill(paddedBytes, (byte) 0);
        }
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return BigDecimal.ZERO;
        }
        try {
            // Decrypt as Base64 AES ciphertext
            byte[] decrypted = cryptoService.decrypt(dbData);
            try {
                // Trim trailing spaces from padding
                String paddedStr = new String(decrypted, StandardCharsets.UTF_8);
                return new BigDecimal(paddedStr.trim());
            } finally {
                // Zero out the decrypted bytes
                java.util.Arrays.fill(decrypted, (byte) 0);
            }
        } catch (Exception e) {
            // Retrocompatibility: If it fails, check if the string is just plain numbers
            // from previous schema
            try {
                return new BigDecimal(dbData);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("CRITICAL: Falha na extração de integridade do Saldo Criptografado", e);
            }
        }
    }
}
