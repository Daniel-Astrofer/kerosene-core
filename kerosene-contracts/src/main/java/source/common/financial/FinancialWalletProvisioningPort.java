package source.common.financial;

/**
 * Boundary used by signup/auth flows to request financial onboarding without depending on
 * KFE internals. Implementations may be local adapters in the monolith or remote clients when
 * KFE runs as a separate service.
 */
public interface FinancialWalletProvisioningPort {

    void ensurePrimaryWalletReady(Long userId, String initialAddress);
}
