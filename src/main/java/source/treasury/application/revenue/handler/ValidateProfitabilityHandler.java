package source.treasury.application.revenue.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.treasury.application.revenue.AbstractRevenueCollectionHandler;
import source.treasury.application.revenue.RevenueCollectionContext;

import java.math.BigDecimal;

@Component
public class ValidateProfitabilityHandler extends AbstractRevenueCollectionHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidateProfitabilityHandler.class);
    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");

    @Override
    protected void doHandle(RevenueCollectionContext context) {
        long profit = context.userFeeSats() - context.networkFeeSats();
        if (profit <= 0) {
            log.warn("[RevenueCollector] Spread negativo ou zero. Sem lucro coletável. Net={} User={}",
                    context.networkFeeSats(),
                    context.userFeeSats());
            context.stop();
            return;
        }

        context.setProfitSats(profit);
        context.setProfitBtc(BigDecimal.valueOf(profit).divide(SATOSHIS_PER_BITCOIN));
    }
}
