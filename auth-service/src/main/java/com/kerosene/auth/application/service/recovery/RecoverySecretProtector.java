package com.kerosene.auth.application.service.recovery;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.kerosene.auth.AuthConstants;
import com.kerosene.auth.application.service.crypto.contracts.Cryptography;
import com.kerosene.auth.application.service.crypto.contracts.Hasher;
import com.kerosene.auth.application.service.validation.totp.contracts.TOTPKeyGenerate;
import com.kerosene.security.infra.VaultKeyProvider;

/** Hashes replacement passphrases and encrypts replacement TOTP seeds before temporary storage. */
@Service
public class RecoverySecretProtector {

    /** Argon2-qualified hasher for the proposed account passphrase. */
    private final Hasher hasher;
    /** Generator for a new TOTP secret during recovery. */
    private final TOTPKeyGenerate totpGenerator;
    /** AES-256 cryptography service for protecting the temporary TOTP seed. */
    private final Cryptography cryptography;
    /** Provides the master key used to encrypt/decrypt the temporary seed. */
    private final VaultKeyProvider vaultKeyProvider;

    /** Creates the protector with hashing, TOTP, AES, and Vault-key dependencies. */
    /** @param hasher Argon2 passphrase hasher */
    /** @param totpGenerator TOTP secret generator */
    /** @param cryptography AES-256 encryption service */
    /** @param vaultKeyProvider master-key provider */
    public RecoverySecretProtector(@Qualifier("Argon2Hasher") Hasher hasher,
            TOTPKeyGenerate totpGenerator,
            @Qualifier("aes256") Cryptography cryptography,
            VaultKeyProvider vaultKeyProvider) {
        this.hasher = hasher;
        this.totpGenerator = totpGenerator;
        this.cryptography = cryptography;
        this.vaultKeyProvider = vaultKeyProvider;
    }

    /** Generates a new TOTP seed, hashes the proposed passphrase, encrypts the seed, and builds its enrollment URI. */
    /** @param normalizedUsername account name used in the authenticator URI */
    /** @param newPassphrase proposed passphrase characters */
    /** @return protected temporary recovery secrets and enrollment URI */
    public PreparedRecoverySecrets prepare(String normalizedUsername, char[] newPassphrase) {
        String totpSecret = totpGenerator.keyGenerator();
        return new PreparedRecoverySecrets(
                hashPassphrase(newPassphrase),
                encryptTotpSecret(totpSecret),
                buildOtpUri(normalizedUsername, totpSecret));
    }

    /** Decrypts the stored TOTP seed and clears the decrypted byte buffer before returning text. */
    /** @param encryptedTotpSecret Base64-encoded ciphertext */
    /** @return recovered UTF-8 TOTP seed */
    /** @throws IllegalStateException when decoding, key retrieval, or decryption fails */
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

    /** Formats the configured authenticator URI using app label and account name. */
    /** @param normalizedUsername canonical account name */
    /** @param totpSecret generated seed */
    /** @return otpauth URI */
    private String buildOtpUri(String normalizedUsername, String totpSecret) {
        return String.format(
                AuthConstants.TOTP_URI_FORMAT,
                AuthConstants.APP_NAME,
                normalizedUsername,
                totpSecret,
                AuthConstants.APP_NAME);
    }

    /** Encrypts the UTF-8 TOTP seed under the current Vault master key and Base64-encodes ciphertext. */
    /** @param totpSecret generated seed */
    /** @return Base64 ciphertext */
    /** @throws IllegalStateException when key retrieval or encryption fails */
    private String encryptTotpSecret(String totpSecret) {
        try {
            byte[] encrypted = cryptography.encrypt(totpSecret.getBytes(StandardCharsets.UTF_8),
                    vaultKeyProvider.getMasterKey());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to protect the recovery TOTP seed in Redis.", e);
        }
    }

    /** Hashes an independent passphrase copy and zeroes the copied character array in all cases. */
    /** @param passphrase proposed passphrase */
    /** @return encoded password hash */
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

    /** Copies mutable passphrase input so hashing cleanup does not mutate the caller's array. */
    /** @param input caller-owned characters */
    /** @return independent array or null */
    private char[] copyCharArray(char[] input) {
        if (input == null) {
            return null;
        }
        char[] copy = new char[input.length];
        System.arraycopy(input, 0, copy, 0, input.length);
        return copy;
    }

    /**
     * Protected values held temporarily in the recovery session and the URI shown to the client.
     * @param hashedPassphrase replacement passphrase hash
     * @param encryptedTotpSecret encrypted replacement seed
     * @param otpUri authenticator enrollment URI
     */
    public record PreparedRecoverySecrets(String hashedPassphrase, String encryptedTotpSecret, String otpUri) {
    }
}
