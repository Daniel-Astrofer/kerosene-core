package com.kerosene.common.admin;

import java.time.Instant;

/**
 * Service interface for provider connection admin operations.
 */
public interface AdminProviderService {

    ProviderValidationResult validateConnection(String connectionId);

    record ProviderValidationResult(
            String connectionId,
            String providerName,
            boolean reachable,
            boolean authenticated,
            long latencyMs,
            Instant checkedAt,
            String details) {}
}
