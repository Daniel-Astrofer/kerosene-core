package source.transactions.dto;

import java.math.BigDecimal;

public record OnchainAddressRequestDTO(
        String walletName,
        BigDecimal expectedAmountBtc) {
}
