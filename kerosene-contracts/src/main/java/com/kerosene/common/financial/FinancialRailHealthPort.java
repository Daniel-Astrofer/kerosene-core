package com.kerosene.common.financial;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface FinancialRailHealthPort {

    ProviderHealth custodyProvider();
    List<ProviderHealth> activeRailProviders();

    enum HealthState {
        DISABLED,
        STARTING,
        DEGRADED,      // working but with reduced capability
        READ_ONLY,      // can observe but cannot spend
        AVAILABLE,      // fully operational
        UNSAFE,         // operational but with known security issue
        UNAVAILABLE     // completely down
    }

    record ProviderHealth(
        String providerName,         // e.g. "bitcoind-mainnet", "lnd-testnet"
        String implementation,       // e.g. "bitcoind:v26.0", "lnd:v0.18"
        HealthState state,
        boolean canRead,             // can query blockchain state
        boolean canReceive,          // can detect incoming transactions
        boolean canSpend,            // can create/sign outgoing transactions
        boolean canReconcile,        // can verify balances
        String network,              // MAINNET/TESTNET/etc
        long syncHeight,             // current block height
        Instant lastSuccessfulProbe, // last health check that passed
        String reasonCode            // why in current state (if not AVAILABLE)
    ) {
        public ProviderHealth {
            if (providerName == null || providerName.isBlank()) throw new IllegalArgumentException("providerName required");
            if (state == null) throw new IllegalArgumentException("state required");
            if (syncHeight < 0) throw new IllegalArgumentException("syncHeight must be >= 0");
        }
    }

    // --- Deprecated: old method and record kept for backward compatibility ---

    /**
     * @deprecated Use {@link #custodyProvider()} returning {@link ProviderHealth} instead.
     */
    @Deprecated(forRemoval = true)
    default ProviderStatus custodyProviderLegacy() {
        throw new UnsupportedOperationException("Use custodyProvider() returning ProviderHealth instead.");
    }

    /**
     * @deprecated Use {@link #activeRailProviders()} returning {@code List<ProviderHealth>} instead.
     */
    @Deprecated(forRemoval = true)
    default Map<String, ProviderStatus> activeRailProvidersLegacy() {
        throw new UnsupportedOperationException("Use activeRailProviders() returning List<ProviderHealth> instead.");
    }

    /**
     * @deprecated Replaced by {@link ProviderHealth} with typed {@link HealthState} enum
     *             and granular capability flags.
     */
    @Deprecated(forRemoval = true)
    record ProviderStatus(String providerName, boolean live, String implementation) {
        // kept for backward compatibility only
    }
}
