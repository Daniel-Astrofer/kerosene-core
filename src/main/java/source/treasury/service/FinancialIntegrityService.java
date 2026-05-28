package source.treasury.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.treasury.application.port.in.PerformFinancialAuditUseCase;
import source.treasury.application.port.out.CircuitBreakerPort;

@Service
public class FinancialIntegrityService {

    private final PerformFinancialAuditUseCase performFinancialAuditUseCase;
    private final CircuitBreakerPort circuitBreakerPort;

    public FinancialIntegrityService(
            PerformFinancialAuditUseCase performFinancialAuditUseCase,
            CircuitBreakerPort circuitBreakerPort) {
        this.performFinancialAuditUseCase = performFinancialAuditUseCase;
        this.circuitBreakerPort = circuitBreakerPort;
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void performFinancialAudit() {
        performFinancialAuditUseCase.performAudit();
    }

    public boolean isDepositsHalted() {
        return circuitBreakerPort.isDepositsHalted();
    }

    public boolean isWithdrawalsHalted() {
        return circuitBreakerPort.isWithdrawalsHalted();
    }

    public void resetCircuitBreakers() {
        circuitBreakerPort.reset();
    }

}
