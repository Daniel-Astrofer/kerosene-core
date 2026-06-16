package source.auth.application.service.validation.totp;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;
import source.auth.AuthExceptions;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.security.VaultKeyProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TOTPValidatorTest {

    @Test
    void totpVerifyAcceptsPlainBase32SignupSecret() {
        TOTPValidator validator = newValidator();
        String secret = "JBSWY3DPEHPK3PXP";

        assertDoesNotThrow(() -> validator.totpVerify(secret, currentTotp(secret)));
    }

    @Test
    void totpVerifyRejectsPlainBase32WrongCodeWithoutDecryptionError() throws Exception {
        Cryptography cryptography = mock(Cryptography.class);
        when(cryptography.decrypt(any(byte[].class), any())).thenThrow(new RuntimeException("boom"));
        TOTPValidator validator = new TOTPValidator(
                mock(RedisServicer.class),
                cryptography,
                mock(VaultKeyProvider.class));

        String secret = "JBSWY3DPEHPK3PXP";
        String wrongCode = currentTotp(secret).equals("000000") ? "111111" : "000000";

        assertThrows(
                AuthExceptions.IncorrectTotpException.class,
                () -> validator.totpVerify(secret, wrongCode));
    }

    private TOTPValidator newValidator() {
        return new TOTPValidator(
                mock(RedisServicer.class),
                mock(Cryptography.class),
                mock(VaultKeyProvider.class));
    }

    private String currentTotp(String secret) {
        try {
            return generateTotp(new Base32().decode(secret), System.currentTimeMillis() / 30000);
        } catch (Exception exception) {
            throw new AssertionError("Failed to build test TOTP.", exception);
        }
    }

    private String generateTotp(byte[] key, long timeWindow) throws Exception {
        byte[] data = new byte[8];
        long value = timeWindow;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xF;
        long truncatedHash = 0;
        for (int i = 0; i < 4; i++) {
            truncatedHash <<= 8;
            truncatedHash |= hash[offset + i] & 0xFF;
        }
        truncatedHash &= 0x7FFFFFFF;
        return String.format("%06d", truncatedHash % 1000000);
    }
}
