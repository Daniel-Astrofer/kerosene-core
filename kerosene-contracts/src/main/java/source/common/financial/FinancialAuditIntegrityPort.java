package source.common.financial;

import java.time.LocalDateTime;

public interface FinancialAuditIntegrityPort {

    AuditRoot root();

    record AuditRoot(
            String merkleRoot,
            long eventCount,
            Long fromSequence,
            Long toSequence,
            LocalDateTime generatedAt) {
    }
}
