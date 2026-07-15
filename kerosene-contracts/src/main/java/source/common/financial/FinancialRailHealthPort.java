package source.common.financial;

import java.util.Map;

public interface FinancialRailHealthPort {

    ProviderStatus custodyProvider();

    Map<String, ProviderStatus> activeRailProviders();

    record ProviderStatus(String providerName, boolean live, String implementation) {
    }
}
