package source.treasury.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TreasuryConfigState(
        BigDecimal maxWithdrawLimit,
        String auditXpub,
        LocalDateTime updatedAt) {
}
