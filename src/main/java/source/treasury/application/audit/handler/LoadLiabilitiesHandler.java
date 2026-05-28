package source.treasury.application.audit.handler;

import org.springframework.stereotype.Component;
import source.treasury.application.audit.AbstractFinancialAuditHandler;
import source.treasury.application.audit.FinancialAuditContext;
import source.treasury.application.port.out.LedgerLiabilityPort;

import java.math.BigDecimal;

@Component
public class LoadLiabilitiesHandler extends AbstractFinancialAuditHandler {

    private final LedgerLiabilityPort ledgerLiabilityPort;

    public LoadLiabilitiesHandler(LedgerLiabilityPort ledgerLiabilityPort) {
        this.ledgerLiabilityPort = ledgerLiabilityPort;
    }

    @Override
    protected void doHandle(FinancialAuditContext context) {
        BigDecimal totalLiabilities = ledgerLiabilityPort.loadTotalLiabilities();
        context.setTotalLiabilitiesBtc(totalLiabilities != null ? totalLiabilities : BigDecimal.ZERO);
    }
}
