package source.treasury.application.revenue.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.treasury.application.revenue.AbstractRevenueCollectionHandler;
import source.treasury.application.revenue.RevenueCollectionContext;

@Component
public class LogRevenueCollectionHandler extends AbstractRevenueCollectionHandler {

    private static final Logger log = LoggerFactory.getLogger(LogRevenueCollectionHandler.class);

    @Override
    protected void doHandle(RevenueCollectionContext context) {
        log.info("[RevenueCollector] Profit interceptado: {} sats. Redirecionado para address auditado: {}",
                context.profitSats(),
                context.auditAddress());
    }
}
