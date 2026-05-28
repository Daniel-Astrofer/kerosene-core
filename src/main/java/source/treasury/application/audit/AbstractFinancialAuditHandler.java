package source.treasury.application.audit;

import source.treasury.application.chain.AbstractChainHandler;

public abstract class AbstractFinancialAuditHandler extends AbstractChainHandler<FinancialAuditContext>
        implements FinancialAuditHandler {
}
