package source.config.production;

import source.common.financial.FinancialRailProductionSafetyPort;

public class ExternalRailProviderProductionSafetyCheck extends AbstractProductionSafetyCheck {

    public ExternalRailProviderProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        FinancialRailProductionSafetyPort safetyPort = context.beanFactory()
                .getBeanProvider(FinancialRailProductionSafetyPort.class)
                .getIfAvailable();
        if (safetyPort == null) {
            context.addViolation("Financial rail production safety port must be available in prod");
            return;
        }
        safetyPort.collectProductionViolations().forEach(context::addViolation);
    }
}
