package source.ledger.dto;

import java.math.BigDecimal;

public record TreasuryAuditConfigRequestDTO(
        BigDecimal maxWithdrawLimit,
        String auditXpub) {
}
