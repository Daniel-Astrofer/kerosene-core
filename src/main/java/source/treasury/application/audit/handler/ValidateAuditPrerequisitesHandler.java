package source.treasury.application.audit.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.treasury.application.audit.AbstractFinancialAuditHandler;
import source.treasury.application.audit.FinancialAuditContext;
import source.treasury.application.port.out.VaultReadinessPort;

@Component
public class ValidateAuditPrerequisitesHandler extends AbstractFinancialAuditHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidateAuditPrerequisitesHandler.class);
    private final VaultReadinessPort vaultReadinessPort;

    public ValidateAuditPrerequisitesHandler(VaultReadinessPort vaultReadinessPort) {
        this.vaultReadinessPort = vaultReadinessPort;
    }

    @Override
    protected void doHandle(FinancialAuditContext context) {
        if (!vaultReadinessPort.isReady()) {
            log.info("[Financial Audit] Skipping audit loop: Vault master key is not available yet.");
            context.stop();
            return;
        }
        if (!context.solvencyAuditEnforced()) {
            log.info("[Financial Audit] Solvency enforcement disabled for this profile. Skipping reserve audit loop.");
            context.stop();
        }
    }
}
