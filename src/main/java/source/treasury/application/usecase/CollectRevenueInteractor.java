package source.treasury.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.treasury.application.port.in.CollectRevenueUseCase;
import source.treasury.application.revenue.RevenueCollectionHandler;
import source.treasury.application.revenue.RevenueCollectionContext;
import source.treasury.application.revenue.handler.AppendMerkleEntryHandler;
import source.treasury.application.revenue.handler.AssignAuditAddressHandler;
import source.treasury.application.revenue.handler.LogRevenueCollectionHandler;
import source.treasury.application.revenue.handler.PersistRevenueHandler;
import source.treasury.application.revenue.handler.ValidateProfitabilityHandler;
import source.treasury.domain.model.RevenueCollectionResult;

@Service
@Transactional(rollbackFor = Exception.class)
public class CollectRevenueInteractor implements CollectRevenueUseCase {

    private final RevenueCollectionHandler chain;

    public CollectRevenueInteractor(
            ValidateProfitabilityHandler validateProfitabilityHandler,
            PersistRevenueHandler persistRevenueHandler,
            AppendMerkleEntryHandler appendMerkleEntryHandler,
            AssignAuditAddressHandler assignAuditAddressHandler,
            LogRevenueCollectionHandler logRevenueCollectionHandler) {
        validateProfitabilityHandler
                .linkWith(persistRevenueHandler)
                .linkWith(appendMerkleEntryHandler)
                .linkWith(assignAuditAddressHandler)
                .linkWith(logRevenueCollectionHandler);

        this.chain = validateProfitabilityHandler;
    }

    @Override
    public RevenueCollectionResult collectProfit(long networkFeeSats, long userFeeSats) {
        RevenueCollectionContext context = new RevenueCollectionContext(networkFeeSats, userFeeSats);
        chain.handle(context);
        return context.toResult();
    }
}
