package source.treasury.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.treasury.application.audit.FinancialAuditContext;
import source.treasury.application.audit.FinancialAuditHandler;
import source.treasury.application.audit.handler.CaptureReserveSnapshotHandler;
import source.treasury.application.audit.handler.EvaluateSolvencyHandler;
import source.treasury.application.audit.handler.LoadLiabilitiesHandler;
import source.treasury.application.audit.handler.TriggerCircuitBreakerHandler;
import source.treasury.application.audit.handler.ValidateAuditPrerequisitesHandler;
import source.treasury.application.port.in.PerformFinancialAuditUseCase;
import source.treasury.domain.model.FinancialAuditResult;

import java.math.BigDecimal;

@Service
public class PerformFinancialAuditInteractor implements PerformFinancialAuditUseCase {

    private static final Logger log = LoggerFactory.getLogger(PerformFinancialAuditInteractor.class);

    private final FinancialAuditHandler chain;
    private final boolean solvencyAuditEnforced;
    private final BigDecimal driftTolerance;

    public PerformFinancialAuditInteractor(
            ValidateAuditPrerequisitesHandler validateAuditPrerequisitesHandler,
            LoadLiabilitiesHandler loadLiabilitiesHandler,
            CaptureReserveSnapshotHandler captureReserveSnapshotHandler,
            EvaluateSolvencyHandler evaluateSolvencyHandler,
            TriggerCircuitBreakerHandler triggerCircuitBreakerHandler,
            @Value("${audit.solvency.enforced:true}") boolean solvencyAuditEnforced,
            @Value("${audit.solvency.drift-tolerance-btc:0.001}") BigDecimal driftTolerance) {
        validateAuditPrerequisitesHandler
                .linkWith(loadLiabilitiesHandler)
                .linkWith(captureReserveSnapshotHandler)
                .linkWith(evaluateSolvencyHandler)
                .linkWith(triggerCircuitBreakerHandler);

        this.chain = validateAuditPrerequisitesHandler;
        this.solvencyAuditEnforced = solvencyAuditEnforced;
        this.driftTolerance = driftTolerance;
    }

    @Override
    public FinancialAuditResult performAudit() {
        FinancialAuditContext context = new FinancialAuditContext(solvencyAuditEnforced, driftTolerance);
        try {
            chain.handle(context);
            return context.toResult();
        } catch (Exception ex) {
            log.error("[Financial Audit] FAILED to perform audit loop: {}", ex.getMessage());
            return new FinancialAuditResult(false, false, BigDecimal.ZERO, null, ex.getMessage());
        }
    }
}
