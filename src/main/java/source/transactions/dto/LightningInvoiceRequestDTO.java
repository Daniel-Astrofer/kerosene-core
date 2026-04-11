package source.transactions.dto;

import java.math.BigDecimal;

public record LightningInvoiceRequestDTO(
        String walletName,
        BigDecimal amount,
        String memo,
        Integer expiresInSeconds) {
}
