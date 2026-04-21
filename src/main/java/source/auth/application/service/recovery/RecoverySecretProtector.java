package source.auth.application.service.recovery;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import source.auth.AuthConstants;
import source.auth.application.service.cripto.contracts.Cryptography;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.validation.totp.contracts.TOTPKeyGenerate;
import source.security.VaultKeyProvider;

@Service
public class RecoverySecretProtector {

    private final Hasher hasher;
    private final TOTPKeyGenerate totpGenerator;
    private final Cryptography cryptography;
    private final VaultKeyProvider vaultKeyProvider;

    public RecoverySecretProtector(@Qualifier("Argon2Hasher") Hasher hasher,
            TOTPKeyGenerate totpGenerator,
            @Qualifier("aes256") Cryptography cryptography,
            VaultKeyProvider vaultKeyProvider) {
        this.hasher = hasher;
        this.totpGenerator = totpGenerator;
        this.cryptography = cryptography;
        this.vaultKeyProvider = vaultKeyProvider;
    }

    public PreparedRecoverySecrets prepare(String normalizedUsername, char[] newPassphrase) {
        String totpSecret = totpGenerator.keyGenerator();
        return new PreparedRecoverySecrets(
                hashPassphrase(newPassphrase),
                encryptTotpSecret(totpSecret),
                buildOtpUri(normalizedUsername, totpSecret));
    }

    public String recoverTotpSecret(String encryptedTotpSecret) {
        try {
            byte[] decrypted = cryptography.decrypt(Base64.getDecoder().decode(encryptedTotpSecret),
                    vaultKeyProvider.getMasterKey());
            try {
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(decrypted, (byte) 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to recover the protected TOTP seed.", e);
        }
    }

    private String buildOtpUri(String normalizedUsername, String totpSecret) {
        return String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                normalizedUsername,
                totpSecret,
                AuthConstants.APP_NAME);
    }

    private String encryptTotpSecret(String totpSecret) {
        try {
            byte[] encrypted = cryptography.encrypt(totpSecret.getBytes(StandardCharsets.UTF_8),
                    vaultKeyProvider.getMasterKey());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to protect the recovery TOTP seed in Redis.", e);
        }
    }

    private String hashPassphrase(char[] passphrase) {
        char[] copy = copyCharArray(passphrase);
        try {
            return hasher.hash(copy);
        } finally {
            if (copy != null) {
                Arrays.fill(copy, '\0');
            }
        }
    }

    private char[] copyCharArray(char[] input) {
        if (input == null) {
            return null;
        }
        char[] copy = new char[input.length];
        System.arraycopy(input, 0, copy, 0, input.length);
        return copy;
    }

    public record PreparedRecoverySecrets(String hashedPassphrase, String encryptedTotpSecret, String otpUri) {
    }
}
