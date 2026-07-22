package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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
    private volatile byte[] masterKeyBytes;

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

    public void storeMasterKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Master key must be " + KEY_BYTES + " bytes. Got: "
                            + (keyBytes == null ? "null" : keyBytes.length));
        }

        keyLock.writeLock().lock();
        try {
            if (masterKeyBytes != null) {
                zeroKeyBytes(masterKeyBytes);
            }
            masterKeyBytes = Arrays.copyOf(keyBytes, keyBytes.length);
            logger.info("[MasterKeyMemoryStore] Master key securely locked in RAM.");
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    public void destroyMasterKey() {
        keyLock.writeLock().lock();
        try {
            if (masterKeyBytes == null) {
                return;
            }
            zeroKeyBytes(masterKeyBytes);
            masterKeyBytes = null;
            logger.info("[MasterKeyMemoryStore] Master key bytes zeroed and reference nulled.");
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    private void zeroKeyBytes(byte[] keyBytes) {
        if (keyBytes == null) {
            return;
        }
        Arrays.fill(keyBytes, (byte) 0);
    }
}
