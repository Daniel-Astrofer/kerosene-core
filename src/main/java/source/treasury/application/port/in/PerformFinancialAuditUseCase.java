package source.treasury.application.port.in;

import source.treasury.domain.model.FinancialAuditResult;

public interface PerformFinancialAuditUseCase {

    FinancialAuditResult performAudit();
}
