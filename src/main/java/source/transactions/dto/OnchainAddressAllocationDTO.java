package source.transactions.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OnchainAddressAllocationDTO(
        String walletName,
        String onchainAddress,
        BigDecimal expectedAmountBtc,
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
