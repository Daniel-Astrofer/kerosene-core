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
        String invoiceId,
        String blockchainTxid,
        String paymentHash,
        String invoiceData,
        BigDecimal amountBtc,
        BigDecimal networkFeeBtc,
        BigDecimal platformFeeBtc,
        BigDecimal totalDebitedBtc,
        String externalReference,
        Integer confirmations,
        LocalDateTime expiresAt,
        LocalDateTime detectedAt,
        LocalDateTime settledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String context) {
}
