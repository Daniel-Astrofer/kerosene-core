package com.kerosene.common.security;

/**
 * Port for column-level encryption using KMS-bound keys.
 *
 * <p>The master key never leaves the KMS/HSM boundary.
 * All operations use {@link EncryptedValue} with AAD context binding.
 *
 * <h3>Migration from legacy API</h3>
 * <p>Legacy {@link #encrypt(byte[])} and {@link #decrypt(String)} are deprecated.
 * Implementations must migrate to the new purpose-bound API using
 * {@link #encrypt(CryptoPurpose, byte[], byte[])} and
 * {@link #decrypt(CryptoPurpose, EncryptedValue, byte[])}.</p>
 */
public interface StringColumnCryptoPort {

    // ── New API (purpose-bound, KMS-safe) ────────────────────────────────

    /**
     * Encrypts plaintext with AAD context binding.
     *
     * @param purpose       domain separation tag
     * @param plaintext     the data to encrypt
     * @param associatedData additional authenticated data (table+column+entityId+tenantId)
     * @return sealed encrypted container
     */
    EncryptedValue encrypt(CryptoPurpose purpose, byte[] plaintext, byte[] associatedData);

    /**
     * Decrypts an {@link EncryptedValue} with AAD context binding.
     *
     * @param purpose       must match the purpose used at encryption time
     * @param encrypted     the sealed container
     * @param associatedData must match the AAD used at encryption time
     * @return original plaintext
     */
    byte[] decrypt(CryptoPurpose purpose, EncryptedValue encrypted, byte[] associatedData);

    /**
     * Re-wraps (rotates) the encrypted value without exposing plaintext.
     * Used for key rotation when the underlying KMS key changes.
     *
     * @param value the currently sealed value
     * @return a new EncryptedValue sealed under the current KMS key version
     */
    EncryptedValue rewrap(EncryptedValue value);

    /**
     * Checks whether the encrypted value should be rotated.
     *
     * @param value the currently sealed value
     * @return true if the KMS key version or algorithm is stale
     */
    boolean needsRotation(EncryptedValue value);

    // ── Legacy API (deprecated — migrate to purpose-bound methods) ────────

    /**
     * @deprecated Use {@link #encrypt(CryptoPurpose, byte[], byte[])} with
     *             {@code CryptoPurpose.COLUMN_ENCRYPTION} and proper AAD.
     *             This method will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    default String encrypt(byte[] plainBytes) {
        throw new UnsupportedOperationException(
            "Direct encrypt(byte[]) is removed. Migrate to encrypt(CryptoPurpose, byte[], byte[]) with AAD.");
    }

    /**
     * @deprecated Use {@link #decrypt(CryptoPurpose, EncryptedValue, byte[])} with
     *             {@code CryptoPurpose.COLUMN_ENCRYPTION} and matching AAD.
     *             This method will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    default byte[] decrypt(String encryptedValue) {
        throw new UnsupportedOperationException(
            "Direct decrypt(String) is removed. Migrate to decrypt(CryptoPurpose, EncryptedValue, byte[]) with AAD.");
    }

    /**
     * @deprecated The master key must NEVER leave the KMS/HSM boundary.
     *             Use {@link #rewrap(EncryptedValue)} for key rotation instead.
     *             This method will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    default byte[] getMasterKeyBytes() {
        throw new UnsupportedOperationException(
            "getMasterKeyBytes() is removed. The master key must never leave the KMS/HSM boundary.");
    }
}
