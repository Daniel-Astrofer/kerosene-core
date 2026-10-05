package com.kerosene.admin.service;

import java.time.Instant;

/** Read-only administrative contract for the latest financial reconciliation run. */
public interface AdminReconciliationService {

    /**
     * Returns current status and aggregate discrepancy counts.
     *
     * @return latest reconciliation snapshot
     */
    ReconciliationStatus status();

    /**
     * Summary of the most recent reconciliation run.
     *
     * @param status current run status label
     * @param lastRunAt completion/start instant of the latest run, when known
     * @param totalDiscrepancies number of discrepancies detected
     * @param resolvedCount number of discrepancies resolved
     * @param pendingCount number of unresolved discrepancies
     * @param lastRunSummary human-readable summary of the latest run
     */
    record ReconciliationStatus(
            String status,
            Instant lastRunAt,
            long totalDiscrepancies,
            long resolvedCount,
            long pendingCount,
            String lastRunSummary) {}
}
