package source.treasury.domain.service;

public class LiquidityRebalancePolicy {

    private static final double MIN_INBOUND_LIQUIDITY_PCT = 0.20;

    public boolean requiresLoopOut(long localBalance, long remoteBalance) {
        long totalCapacity = localBalance + remoteBalance;
        if (totalCapacity == 0L) {
            return false;
        }
        double remotePct = (double) remoteBalance / totalCapacity;
        return remotePct < MIN_INBOUND_LIQUIDITY_PCT;
    }

    public double inboundLiquidityPercentage(long localBalance, long remoteBalance) {
        long totalCapacity = localBalance + remoteBalance;
        if (totalCapacity == 0L) {
            return 0D;
        }
        return (double) remoteBalance / totalCapacity;
    }

    public double minimumInboundLiquidityPercentage() {
        return MIN_INBOUND_LIQUIDITY_PCT;
    }
}
