package com.kerosene.common.financial;

/**
 * Boundary used by signup/auth flows to request financial onboarding without depending on
 * KFE internals. Implementations may be local adapters in the monolith or remote clients when
 * KFE runs as a separate service.
 */
public interface FinancialWalletProvisioningPort {

    /**
     * Idempotently creates or repairs the user's primary wallet.
     *
     * @param userId persisted Core user identifier
     * @param initialAddress optional pre-issued address; {@code null} lets KFE create or recover it
     */
    void ensurePrimaryWalletReady(Long userId, String initialAddress);
}
