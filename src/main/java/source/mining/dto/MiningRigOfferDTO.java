package source.mining.dto;

import java.math.BigDecimal;

public record MiningRigOfferDTO(
        Long id,
        String rigCode,
        String displayName,
        String algorithm,
        String hashUnit,
        BigDecimal availableHashrate,
        BigDecimal pricePerUnitDayBtc,
        BigDecimal projectedBtcYieldPerUnitDay,
        Integer minRentalHours,
        Integer maxRentalHours,
        String provider) {
}
