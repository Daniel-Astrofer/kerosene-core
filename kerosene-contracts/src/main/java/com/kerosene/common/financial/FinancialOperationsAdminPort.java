package com.kerosene.common.financial;

import java.util.List;
import java.util.Map;

/**
 * @deprecated All methods replaced by {@link FinancialOperationsAdminPortV2}
 *             with typed records instead of untyped {@code Map<String, Object>}.
 *             Implementations MUST migrate to the V2 typed contract.
 */
@Deprecated
public interface FinancialOperationsAdminPort {

    @Deprecated
    default Map<String, Object> blockchain() {
        throw new UnsupportedOperationException(
            "Use FinancialOperationsAdminPortV2.blockchain() returning BlockchainStatus instead.");
    }

    @Deprecated
    default Map<String, Object> lightning() {
        throw new UnsupportedOperationException(
            "Use FinancialOperationsAdminPortV2.lightning() returning LightningStatus instead.");
    }

    @Deprecated
    default List<Map<String, Object>> logs(int limit) {
        throw new UnsupportedOperationException(
            "Use FinancialOperationsAdminPortV2.logs(LogsRequest) returning PaginatedLogs instead.");
    }

    @Deprecated
    default Map<String, Object> metrics() {
        throw new UnsupportedOperationException(
            "Use FinancialOperationsAdminPortV2.metrics() returning SystemMetrics instead.");
    }
}
