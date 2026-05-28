package source.treasury.application.revenue.handler;

import org.springframework.stereotype.Component;
import source.treasury.application.port.out.RevenuePersistencePort;
import source.treasury.application.revenue.AbstractRevenueCollectionHandler;
import source.treasury.application.revenue.RevenueCollectionContext;

@Component
public class PersistRevenueHandler extends AbstractRevenueCollectionHandler {

    private final RevenuePersistencePort revenuePersistencePort;

    public PersistRevenueHandler(RevenuePersistencePort revenuePersistencePort) {
        this.revenuePersistencePort = revenuePersistencePort;
    }

    @Override
    protected void doHandle(RevenueCollectionContext context) {
        context.setAccumulatedProfitBtc(revenuePersistencePort.accumulateProfit(context.profitBtc()));
    }
}
