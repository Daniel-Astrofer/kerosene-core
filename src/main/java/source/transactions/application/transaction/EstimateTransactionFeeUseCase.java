package source.transactions.application.transaction;

import org.springframework.stereotype.Service;
import source.transactions.dto.EstimatedFeeDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class EstimateTransactionFeeUseCase {

    private static final long AVERAGE_TX_SIZE_BYTES = 225L;

    private final TransactionFeeRatesPort transactionFeeRatesPort;

    public EstimateTransactionFeeUseCase(TransactionFeeRatesPort transactionFeeRatesPort) {
        this.transactionFeeRatesPort = transactionFeeRatesPort;
    }

    public EstimatedFeeDTO estimate(BigDecimal amount) {
        TransactionFeeRatesPort.RecommendedFeeRates recommendedFees = transactionFeeRatesPort.currentRecommendedFees();

        long fastTotalSats = recommendedFees.fastestFee() * AVERAGE_TX_SIZE_BYTES;
        long standardTotalSats = recommendedFees.standardFee() * AVERAGE_TX_SIZE_BYTES;
        long slowTotalSats = recommendedFees.slowFee() * AVERAGE_TX_SIZE_BYTES;

        BigDecimal fastBtc = satoshisToBtc(fastTotalSats);
        BigDecimal standardBtc = satoshisToBtc(standardTotalSats);
        BigDecimal slowBtc = satoshisToBtc(slowTotalSats);

        EstimatedFeeDTO estimate = new EstimatedFeeDTO(
                recommendedFees.fastestFee(),
                recommendedFees.standardFee(),
                recommendedFees.slowFee(),
                amount.subtract(standardBtc),
                amount.add(standardBtc));
        estimate.setEstimatedFastBtc(fastBtc);
        estimate.setEstimatedStandardBtc(standardBtc);
        estimate.setEstimatedSlowBtc(slowBtc);
        return estimate;
    }

    private BigDecimal satoshisToBtc(long satoshis) {
        return BigDecimal.valueOf(satoshis).divide(BigDecimal.valueOf(100_000_000), 8, RoundingMode.HALF_UP);
    }
}
