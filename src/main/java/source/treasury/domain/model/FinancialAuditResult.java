package source.treasury.domain.model;

import java.math.BigDecimal;

public record FinancialAuditResult(
        boolean executed,
        boolean solvent,
        BigDecimal totalLiabilitiesBtc,
        ReserveSnapshot reserveSnapshot,
        String panicReason) {
}
