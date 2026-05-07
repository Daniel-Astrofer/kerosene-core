package source.transactions.service;

public interface WatchOnlyAddressImportPort {

    String providerName();

    void importWatchOnlyPublicKey(byte[] publicKey, String expectedAddress);
}
