package source.transactions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LightningInvoiceResponseDTO(
        UUID transferId,
        String walletName,
        String paymentRequest,
        String paymentHash,
        String lightningAddress,
        BigDecimal amountBtc,
        String provider,
        LocalDateTime expiresAt,
        String status) {
}
