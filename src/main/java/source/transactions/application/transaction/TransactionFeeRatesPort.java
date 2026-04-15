package source.transactions.application.transaction;

public interface TransactionFeeRatesPort {

    RecommendedFeeRates currentRecommendedFees();

    record RecommendedFeeRates(long fastestFee, long standardFee, long slowFee) {
        public RecommendedFeeRates {
            fastestFee = Math.max(1L, fastestFee);
            standardFee = Math.max(1L, standardFee);
            slowFee = Math.max(1L, slowFee);
        }
    }
}
