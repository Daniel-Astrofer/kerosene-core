package source.treasury.application.port.in;

import source.treasury.domain.model.RevenueCollectionResult;

public interface CollectRevenueUseCase {

    RevenueCollectionResult collectProfit(long networkFeeSats, long userFeeSats);
}
