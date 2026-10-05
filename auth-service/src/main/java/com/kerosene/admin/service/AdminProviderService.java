package com.kerosene.admin.service;

import java.time.Instant;

/** Read-only contract for testing the reachability and authentication state of a provider connection. */
public interface AdminProviderService {

    /**
     * Executes a validation probe for one configured provider connection.
     *
     * @param connectionId provider connection identifier
     * @return result flags, latency, check time, and provider-supplied diagnostic details
     */
    ProviderValidationResult validateConnection(String connectionId);

    /**
     * Snapshot of one provider connectivity check.
     *
     * @param connectionId tested connection identifier
     * @param providerName provider display name
     * @param reachable whether a network-level connection was established
     * @param authenticated whether provider authentication succeeded
     * @param latencyMs elapsed probe duration in milliseconds
     * @param checkedAt instant when the probe result was captured
     * @param details bounded additional diagnostic description
     */
    record ProviderValidationResult(
            String connectionId,
            String providerName,
            boolean reachable,
            boolean authenticated,
            long latencyMs,
            Instant checkedAt,
            String details) {}
}
