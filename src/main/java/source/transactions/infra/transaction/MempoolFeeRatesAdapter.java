package source.transactions.infra.transaction;

import org.springframework.stereotype.Component;
import source.transactions.application.transaction.TransactionFeeRatesPort;
import source.transactions.infra.MempoolClient;

@Component
public class MempoolFeeRatesAdapter implements TransactionFeeRatesPort {

    private final MempoolClient mempoolClient;

    public MempoolFeeRatesAdapter(MempoolClient mempoolClient) {
        this.mempoolClient = mempoolClient;
    }

    @Override
    public RecommendedFeeRates currentRecommendedFees() {
        MempoolClient.RecommendedFees fees = mempoolClient.getRecommendedFees();
        return new RecommendedFeeRates(fees.fastestFee(), fees.halfHourFee(), fees.hourFee());
    }
}
