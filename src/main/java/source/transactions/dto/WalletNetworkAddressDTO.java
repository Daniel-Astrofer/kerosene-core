package source.transactions.dto;

public record WalletNetworkAddressDTO(
        String walletName,
        String onchainAddress,
        String lightningAddress,
        String provider,
        String externalWalletReference) {
}
