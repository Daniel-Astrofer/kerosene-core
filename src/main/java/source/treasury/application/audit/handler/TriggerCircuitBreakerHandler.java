package source.treasury.application.audit.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.treasury.application.audit.AbstractFinancialAuditHandler;
import source.treasury.application.audit.FinancialAuditContext;
import source.treasury.application.port.out.CircuitBreakerPort;

@Component
public class TriggerCircuitBreakerHandler extends AbstractFinancialAuditHandler {

    private static final Logger log = LoggerFactory.getLogger(TriggerCircuitBreakerHandler.class);
    private final CircuitBreakerPort circuitBreakerPort;

    public TriggerCircuitBreakerHandler(CircuitBreakerPort circuitBreakerPort) {
        this.circuitBreakerPort = circuitBreakerPort;
    }

    @Override
    protected void doHandle(FinancialAuditContext context) {
        if (context.panicReason() == null) {
            return;
        }

        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        log.error("EMERGENCY PANIC: PLATFORM CIRCUIT BREAKER TRIPPED!");
        log.error("Reason: {}", context.panicReason());
        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        circuitBreakerPort.haltDeposits();
        circuitBreakerPort.haltWithdrawals();

        log.error("ALERT: Admin intervention required. Platform is now in READ-ONLY mode.");
    }
}
