package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Owns the RAM-resident AES-256 ops-secret key used by auth/crypto services.
 *
 * Treasury custody is vault-mesh; the HashiCorp / Java-vault bootstrap path
 * (attestation + provision) is removed. The key is loaded from
 * {@code api.secret.aes.secret} (AES_SECRET) only.
 */
@Component
public class VaultKeyProvider {

    public static final int KEY_BYTES = 32;

    private static final Logger logger = LoggerFactory.getLogger(VaultKeyProvider.class);

    private final ReentrantReadWriteLock keyLock = new ReentrantReadWriteLock();
    private volatile byte[] masterKeyBytes;

    public VaultKeyProvider(@Value("${api.secret.aes.secret:}") String aesSecretBase64) {
        storeFromBase64(aesSecretBase64);
    }

    public SecretKey getMasterKey() {
        keyLock.readLock().lock();
        try {
            byte[] current = masterKeyBytes;
            if (current == null) {
                throw new IllegalStateException(
                        "[VaultKeyProvider] Master key is not provisioned in RAM yet.");
            }
            return new SecretKeySpec(Arrays.copyOf(current, current.length), "AES");
        } finally {
            keyLock.readLock().unlock();
        }
    }

    public boolean isReady() {
        keyLock.readLock().lock();
        try {
            return masterKeyBytes != null;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    public void destroyMasterKey() {
        keyLock.writeLock().lock();
        try {
            if (masterKeyBytes == null) {
                return;
            }
            Arrays.fill(masterKeyBytes, (byte) 0);
            masterKeyBytes = null;
            logger.info("[VaultKeyProvider] Master key bytes zeroed and reference nulled.");
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    private void storeFromBase64(String aesSecretBase64) {
        if (aesSecretBase64 == null || aesSecretBase64.isBlank()) {
            throw new IllegalStateException(
                    "[VaultKeyProvider] api.secret.aes.secret is not set. Set AES_SECRET.");
        }
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(aesSecretBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "[VaultKeyProvider] api.secret.aes.secret must be valid Base64.", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "[VaultKeyProvider] api.secret.aes.secret must decode to "
                            + KEY_BYTES + " bytes. Got: " + keyBytes.length);
        }
        keyLock.writeLock().lock();
        try {
            masterKeyBytes = Arrays.copyOf(keyBytes, keyBytes.length);
            Arrays.fill(keyBytes, (byte) 0);
            logger.info("[VaultKeyProvider] Ops AES master key locked in RAM ({} bytes).", KEY_BYTES);
        } finally {
            keyLock.writeLock().unlock();
        }
    }
}
