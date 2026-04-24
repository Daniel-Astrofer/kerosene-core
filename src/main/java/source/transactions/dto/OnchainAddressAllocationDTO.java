package source.transactions.dto;

import java.util.UUID;

public record OnchainAddressAllocationDTO(
        String walletName,
        String onchainAddress,
        String network,
        String provider,
        String externalWalletReference,
        String walletMode,
        UUID transferId,
        String transferStatus,
        Integer confirmations,
        Integer requiredConfirmations,
        String blockchainTxid) {
}
