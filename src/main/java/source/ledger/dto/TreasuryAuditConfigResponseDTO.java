package source.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TreasuryAuditConfigResponseDTO(
        BigDecimal maxWithdrawLimit,
        boolean auditXpubConfigured,
        String auditXpubPreview,
        LocalDateTime updatedAt) {
}
