package source.transactions.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * Agente 2 (Ledger): Responsible for ensuring Kerosene's profit margin
 * by calculating pass-through fees with dynamic spreads.
 */
@Service
public class FeeCalculator {

    private final source.transactions.infra.MempoolClient mempoolClient;

    public FeeCalculator(source.transactions.infra.MempoolClient mempoolClient) {
        this.mempoolClient = mempoolClient;
    }

    private static final int AVG_TX_SIZE_VBYTES = 141; // SegWit P2WPKH 1-in 2-out
    private static final long FIXED_OPERATIONAL_SPREAD = 5000; // Fixed satoshi spread
    private static final double VARIABLE_SPREAD_PERCENT = 0.005; // 0.5% for Swap balancing fund

    public source.transactions.infra.MempoolClient.RecommendedFees getCurrentRecommendations() {
        return mempoolClient.getRecommendedFees();
    }

    /**
     * Calculates the withdrawal fee for a specific amount.
     * Formula: (Mempool Fee * Size) + Fixed Spread + (Amount * Variable Spread)
     */
    public long calculateWithdrawalFee(long feePerVByte, BigDecimal amountBtc) {
        long baseMiningFee = (long) AVG_TX_SIZE_VBYTES * feePerVByte;

        // Convert BTC to Sats to calculate percentage spread
        long amountSats = amountBtc.multiply(new BigDecimal("100000000")).longValue();
        long variableSpread = (long) (amountSats * VARIABLE_SPREAD_PERCENT);

        return baseMiningFee + FIXED_OPERATIONAL_SPREAD + variableSpread;
    }

    /**
     * Simple static fee for the monitoring registry.
     */
    public long calculateBaseFee(long feePerVByte) {
        return ((long) AVG_TX_SIZE_VBYTES * feePerVByte) + FIXED_OPERATIONAL_SPREAD;
    }
}
