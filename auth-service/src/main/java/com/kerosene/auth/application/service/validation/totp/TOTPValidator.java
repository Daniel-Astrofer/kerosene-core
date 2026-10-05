package com.kerosene.auth.application.service.validation.totp;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.application.service.crypto.contracts.Cryptography;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPVerifier;
import com.kerosene.security.infra.VaultKeyProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Validates six-digit RFC 6238 codes and decrypts enrollment secrets using the attested Vault key. */
@Service
public class TOTPValidator implements TOTPVerifier {

    /** Redis-backed enrollment and verification state dependency retained for constructor compatibility. */
    private final RedisServicer service;
    /** AES-256 cryptography used to open the stored secret. */
    private final Cryptography cryptography;
    /** Supplies the in-memory master key established after platform attestation. */
    private final VaultKeyProvider vaultKeyProvider;

    /**
     * Creates the validator with Redis access, AES-256 cryptography, and the attested key provider.
     * @param service Redis service used by the surrounding authentication flow
     * @param cryptography AES-256 implementation selected by Spring
     * @param vaultKeyProvider source of the current master key
     */
    public TOTPValidator(RedisServicer service,
            @Qualifier("aes256") Cryptography cryptography,
            VaultKeyProvider vaultKeyProvider) {
        this.service = service;
        this.cryptography = cryptography;
        this.vaultKeyProvider = vaultKeyProvider;
        // Chave vive no VaultKeyProvider (RAM-only, pós-atestação TPM)
    }

    /**
     * Checks a code against the current 30-second counter and one adjacent counter on either side.
     * @param totpSecret Base32 encoded shared secret
     * @param code six-digit authenticator code
     * @return true only when one permitted time window produces the supplied code
     */
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

    /** Generates the six-digit HMAC-SHA1 OTP for one RFC 6238 time counter. */
    /** @param key decoded shared secret bytes */
    /** @param timeWindow Unix time divided by the 30-second step */
    /** @return zero-padded six-digit one-time password */
    /** @throws Exception when the runtime cannot initialize HMAC-SHA1 */
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

    /**
     * Opens a stored secret, accepting a valid legacy plaintext Base32 value as fallback.
     * @param totpSecret Base64 encrypted secret or legacy Base32 secret
     * @param secretKey key used to decrypt the encrypted representation
     * @return plaintext Base32 secret with whitespace removed
     * @throws IllegalStateException when the registration secret is absent
     */
    @Override
    public String totpDecryptedToString(String totpSecret, SecretKey secretKey) {
        if (totpSecret == null) {
            throw new IllegalStateException("TOTP secret is missing from Redis. Registration session may have expired.");
        }
        try {
            byte[] totpCoded = Base64.getDecoder().decode(totpSecret);
            byte[] totp = cryptography.decrypt(totpCoded, secretKey);
            if (totp != null) {
                return new String(totp, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Fall through to plain Base32 validation
        }
        if (totpSecret.matches("^[A-Z2-7\\s]+=*$")) {
            return totpSecret.replaceAll("\\s+", "");
        }
        throw new RuntimeException("Decryption error: invalid TOTP secret format");
    }

    /**
     * Decrypts the enrollment secret and rejects the request when its code does not match.
     * @param totpSecret encrypted or legacy plaintext secret
     * @param totpCode code submitted by the authenticator
     * @throws AuthExceptions.incorrectTotp when the code is invalid
     */
    @Override
    public void totpVerify(String totpSecret, String totpCode) {

        String totp = totpDecryptedToString(totpSecret, vaultKeyProvider.getMasterKey());

        if (!totpMatcher(totp, totpCode)) {
            throw new AuthExceptions.incorrectTotp("Incorrect TOTP code");
        }

    }

}
