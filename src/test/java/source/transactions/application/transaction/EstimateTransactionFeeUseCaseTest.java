package source.transactions.application.transaction;

import org.junit.jupiter.api.Test;
import source.transactions.dto.EstimatedFeeDTO;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstimateTransactionFeeUseCaseTest {

    @Test
    void estimateUsesRecommendedFeesAndReturnsBtcValues() {
        TransactionFeeRatesPort feeRatesPort = mock(TransactionFeeRatesPort.class);
        when(feeRatesPort.currentRecommendedFees())
                .thenReturn(new TransactionFeeRatesPort.RecommendedFeeRates(20L, 10L, 5L));

        EstimateTransactionFeeUseCase useCase = new EstimateTransactionFeeUseCase(feeRatesPort);

        EstimatedFeeDTO estimate = useCase.estimate(new BigDecimal("0.10000000"));

        assertEquals(20L, estimate.getFastSatoshisPerByte());
        assertEquals(10L, estimate.getStandardSatoshisPerByte());
        assertEquals(5L, estimate.getSlowSatoshisPerByte());
        assertEquals(new BigDecimal("0.00004500"), estimate.getEstimatedFastBtc());
        assertEquals(new BigDecimal("0.00002250"), estimate.getEstimatedStandardBtc());
        assertEquals(new BigDecimal("0.00001125"), estimate.getEstimatedSlowBtc());
        assertEquals(new BigDecimal("0.09997750"), estimate.getAmountReceived());
        assertEquals(new BigDecimal("0.10002250"), estimate.getTotalToSend());
    }
}
