package source.treasury.application.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.treasury.application.audit.handler.CaptureReserveSnapshotHandler;
import source.treasury.application.audit.handler.EvaluateSolvencyHandler;
import source.treasury.application.audit.handler.LoadLiabilitiesHandler;
import source.treasury.application.audit.handler.TriggerCircuitBreakerHandler;
import source.treasury.application.audit.handler.ValidateAuditPrerequisitesHandler;
import source.treasury.application.port.in.CaptureReserveSnapshotUseCase;
import source.treasury.application.port.out.CircuitBreakerPort;
import source.treasury.application.port.out.LedgerLiabilityPort;
import source.treasury.application.port.out.VaultReadinessPort;
import source.treasury.domain.model.FinancialAuditResult;
import source.treasury.domain.model.ReserveSnapshot;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformFinancialAuditInteractorTest {

    @Mock
    private VaultReadinessPort vaultReadinessPort;

    @Mock
    private LedgerLiabilityPort ledgerLiabilityPort;

    @Mock
    private CaptureReserveSnapshotUseCase captureReserveSnapshotUseCase;

    @Mock
    private CircuitBreakerPort circuitBreakerPort;

    @Test
    void shouldTripCircuitBreakerWhenLiabilitiesExceedAssets() {
        when(vaultReadinessPort.isReady()).thenReturn(true);
        when(ledgerLiabilityPort.loadTotalLiabilities()).thenReturn(new BigDecimal("1.50000000"));
        when(captureReserveSnapshotUseCase.captureSnapshot()).thenReturn(new ReserveSnapshot(
                new BigDecimal("0.40000000"),
                new BigDecimal("0.30000000"),
                new BigDecimal("0.10000000"),
                new BigDecimal("0.20000000"),
                new BigDecimal("0.80000000"),
                new BigDecimal("1.00000000")));

        PerformFinancialAuditInteractor interactor = new PerformFinancialAuditInteractor(
                new ValidateAuditPrerequisitesHandler(vaultReadinessPort),
                new LoadLiabilitiesHandler(ledgerLiabilityPort),
                new CaptureReserveSnapshotHandler(captureReserveSnapshotUseCase),
                new EvaluateSolvencyHandler(),
                new TriggerCircuitBreakerHandler(circuitBreakerPort),
                true,
                new BigDecimal("0.001"));

        FinancialAuditResult result = interactor.performAudit();

        assertTrue(result.executed());
        assertFalse(result.solvent());
        assertEquals("INSOLVENCY_DETECTED: Ledger liabilities exceed physical reserves!", result.panicReason());

        verify(circuitBreakerPort).haltDeposits();
        verify(circuitBreakerPort).haltWithdrawals();
    }
}
