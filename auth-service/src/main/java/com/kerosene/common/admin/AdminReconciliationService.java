package com.kerosene.common.admin;

import java.time.Instant;

/**
 * Service interface for reconciliation admin operations.
 */
public interface AdminReconciliationService {

    ReconciliationStatus status();

    record ReconciliationStatus(
            String status,
            Instant lastRunAt,
            long totalDiscrepancies,
            long resolvedCount,
            long pendingCount,
            String lastRunSummary) {}
}
