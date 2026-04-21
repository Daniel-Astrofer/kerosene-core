package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Owns the in-memory AES-256 master key and the panic zeroing path.
 */
@Component
public class MasterKeyMemoryStore {

    public static final int KEY_BYTES = 32;

    private static final Logger logger = LoggerFactory.getLogger(MasterKeyMemoryStore.class);

    private final ReentrantReadWriteLock keyLock = new ReentrantReadWriteLock();
    private volatile SecretKey masterKey;

    public SecretKey getMasterKey() {
        keyLock.readLock().lock();
        try {
            if (masterKey == null) {
                throw new IllegalStateException(
                        "[VaultKeyProvider STALL] Master key is not available yet. "
                                + "The Shard is waiting for Vault attestation or network recovery.");
            }
            return masterKey;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    public boolean isReady() {
        keyLock.readLock().lock();
        try {
            return masterKey != null;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    public void storeMasterKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Master key must be " + KEY_BYTES + " bytes. Got: "
                            + (keyBytes == null ? "null" : keyBytes.length));
        }

        keyLock.writeLock().lock();
        try {
            if (masterKey != null) {
                zeroKeyBytes(masterKey);
            }
            masterKey = new SecretKeySpec(keyBytes, "AES");
            logger.info("[MasterKeyMemoryStore] Master key securely locked in RAM.");
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    public void destroyMasterKey() {
        keyLock.writeLock().lock();
        try {
            if (masterKey == null) {
                return;
            }
            zeroKeyBytes(masterKey);
            masterKey = null;
            logger.info("[MasterKeyMemoryStore] Master key bytes zeroed and reference nulled.");
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    private void zeroKeyBytes(SecretKey key) {
        if (!(key instanceof SecretKeySpec)) {
            return;
        }

        try {
            Field field = SecretKeySpec.class.getDeclaredField("key");
            field.setAccessible(true);
            byte[] keyBytes = (byte[]) field.get(key);
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        } catch (Exception e) {
            logger.error("[MasterKeyMemoryStore] CRITICAL: could not zero master key bytes via reflection: {}",
                    e.getMessage());
        }
    }
}
