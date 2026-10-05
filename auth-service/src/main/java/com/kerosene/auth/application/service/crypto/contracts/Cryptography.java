package com.kerosene.auth.application.service.crypto.contracts;

import javax.crypto.SecretKey;

/** Symmetric encryption boundary for secret material protected by a caller-supplied key. */
public interface Cryptography {
    /** Encrypts plaintext using the implementation's authenticated encryption format. */
    /** @param encrypt plaintext bytes */
    /** @param key encryption key */
    /** @return encoded ciphertext, including any nonce required for decryption */
    /** @throws Exception when encryption or key initialization fails */
    byte[] encrypt(byte[] encrypt, SecretKey key) throws Exception;

    /** Decrypts ciphertext produced by {@link #encrypt(byte[], SecretKey)}. */
    /** @param encrypted ciphertext bytes */
    /** @param key decryption key */
    /** @return recovered plaintext */
    /** @throws Exception when the key, ciphertext, or authentication tag is invalid */
    byte[] decrypt(byte[] encrypted, SecretKey key) throws Exception;
}
