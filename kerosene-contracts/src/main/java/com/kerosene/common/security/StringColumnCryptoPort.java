package source.common.security;

public interface StringColumnCryptoPort {

    String encrypt(byte[] plainBytes);

    byte[] decrypt(String encryptedValue);

    byte[] getMasterKeyBytes();
}
