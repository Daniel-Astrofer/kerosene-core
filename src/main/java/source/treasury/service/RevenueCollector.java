package source.treasury.service;

import org.springframework.stereotype.Service;
import source.treasury.application.port.in.CollectRevenueUseCase;

@Service
public class RevenueCollector {

    private final CollectRevenueUseCase collectRevenueUseCase;

    public RevenueCollector(CollectRevenueUseCase collectRevenueUseCase) {
        this.collectRevenueUseCase = collectRevenueUseCase;
    }

    public long collectProfit(long networkFee, long userFee) {
        return collectRevenueUseCase.collectProfit(networkFee, userFee).profitSats();
    }
}
