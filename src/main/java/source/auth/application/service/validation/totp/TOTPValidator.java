package source.auth.application.service.validation.totp;

import source.auth.AuthExceptions;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.security.VaultKeyProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class TOTPValidator implements TOTPVerifier {

    private final RedisServicer service;
    private final Cryptography cryptography;
    private final VaultKeyProvider vaultKeyProvider;

    public TOTPValidator(RedisServicer service,
            @Qualifier("aes256") Cryptography cryptography,
            VaultKeyProvider vaultKeyProvider) {
        this.service = service;
        this.cryptography = cryptography;
        this.vaultKeyProvider = vaultKeyProvider;
        // Chave vive no VaultKeyProvider (RAM-only, pós-atestação TPM)
    }

    @Override
    public boolean totpMatcher(String totpSecret, String code) {
        try {
            // Tolerance configuration: 1 window before and 1 window after (+- 30 seconds)
            int tolerance = 1;

            org.apache.commons.codec.binary.Base32 codec32 = new org.apache.commons.codec.binary.Base32();
            byte[] decodedKey = codec32.decode(totpSecret);

            long currentTimeMillis = System.currentTimeMillis();
            long currentWindow = currentTimeMillis / 30000;

            for (int i = -tolerance; i <= tolerance; i++) {
                long window = currentWindow + i;
                if (generateTotp(decodedKey, window).equals(code)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // RFC 6238 Standard Generator
    private String generateTotp(byte[] key, long timeWindow) throws Exception {
        byte[] data = new byte[8];
        long value = timeWindow;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xF;
        long truncatedHash = 0;
        for (int i = 0; i < 4; ++i) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }
        truncatedHash &= 0x7FFFFFFF;
        long otp = truncatedHash % 1000000;
        return String.format("%06d", otp);
    }

    @Override
    public String totpDecryptedToString(String totpSecret, SecretKey secretKey) {

        if (totpSecret == null) {
            throw new IllegalStateException("TOTP secret is missing from Redis. Registration session may have expired.");
        }
        byte[] totpCoded = Base64.getDecoder().decode(totpSecret);
        try {
            byte[] totp = cryptography.decrypt(totpCoded, secretKey);
            return new String(totp, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption error: ");
        }
    }

    @Override
    public void totpVerify(String totpSecret, String totpCode) {

        String totp = totpDecryptedToString(totpSecret, vaultKeyProvider.getMasterKey());

        if (!totpMatcher(totp, totpCode)) {
            throw new AuthExceptions.incorrectTotp("Incorrect TOTP code");
        }

    }

}
