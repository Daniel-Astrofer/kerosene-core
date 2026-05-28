package source.treasury.domain.service;

public class FeeMarkupPolicy {

    private static final double NETWORK_MULTIPLICATOR = 1.10;
    private static final long FIXED_FEE_SATS = 500;

    public long apply(long estimatedNetworkFeeSats) {
        return (long) Math.ceil(estimatedNetworkFeeSats * NETWORK_MULTIPLICATOR) + FIXED_FEE_SATS;
    }
}
