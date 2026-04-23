package source.transactions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExternalTransferResponseDTO(
        UUID id,
        String network,
        String transferType,
        String status,
        String provider,
        String walletName,
        String destination,
        BigDecimal amountBtc,
        BigDecimal networkFeeBtc,
        BigDecimal platformFeeBtc,
        BigDecimal totalDebitedBtc,
        String externalReference,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String context) {
}
