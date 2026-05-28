package source.transactions.dto;

public record WalletNetworkAddressDTO(
        String walletName,
        String onchainAddress,
        String lightningAddress,
        String network,
        String provider,
        String externalWalletReference,
        String walletMode,
        boolean lightningEnabled,
        String lightningUnavailableReason) {
}
