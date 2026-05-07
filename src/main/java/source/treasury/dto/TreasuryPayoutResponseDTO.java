package source.treasury.dto;

import source.ledger.entity.SiphonRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TreasuryPayoutResponseDTO(
        UUID id,
        String status,
        BigDecimal amount,
        String destinationAddress,
        String idempotencyKey,
        LocalDateTime requestedAt,
        LocalDateTime executableAfter,
        LocalDateTime approvedAt,
        LocalDateTime queuedAt,
        LocalDateTime executedAt,
        String providerReference,
        String blockchainTxid,
        String providerStatus,
        int attempts,
        boolean retryable,
        String lastError) {

    public static TreasuryPayoutResponseDTO from(SiphonRequest request) {
        return new TreasuryPayoutResponseDTO(
                request.getId(),
                request.getStatus() != null ? request.getStatus().name() : null,
                request.getAmount(),
                request.getDestinationAddress(),
                request.getIdempotencyKey(),
                request.getRequestedAt(),
                request.getExecutableAfter(),
                request.getApprovedAt(),
                request.getQueuedAt(),
                request.getExecutedAt(),
                request.getProviderReference(),
                request.getBlockchainTxid(),
                request.getProviderStatus(),
                request.getAttempts(),
                request.isRetryable(),
                request.getLastError());
    }
}
