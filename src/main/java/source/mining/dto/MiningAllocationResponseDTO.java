package source.mining.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MiningAllocationResponseDTO(
        UUID id,
        Long rigId,
        String rigName,
        String walletName,
        String algorithm,
        BigDecimal allocatedHashrate,
        String hashUnit,
        Integer durationHours,
        BigDecimal rentalCostBtc,
        BigDecimal projectedGrossYieldBtc,
        BigDecimal projectedNetYieldBtc,
        BigDecimal refundedAmountBtc,
        String status,
        String providerRentalReference,
        String payoutAddress,
        String poolUrl,
        String workerName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime settledAt) {
}
