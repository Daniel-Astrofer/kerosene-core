package source.treasury.domain.model;

import java.math.BigDecimal;

public record RevenueCollectionResult(
        long profitSats,
        BigDecimal profitBtc,
        BigDecimal accumulatedProfitBtc,
        String merkleRoot,
        String auditAddress) {
}
