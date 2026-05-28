package source.treasury.application.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.treasury.application.port.out.AuditAddressPort;
import source.treasury.application.port.out.MerkleLedgerPort;
import source.treasury.application.port.out.RevenuePersistencePort;
import source.treasury.application.revenue.handler.AppendMerkleEntryHandler;
import source.treasury.application.revenue.handler.AssignAuditAddressHandler;
import source.treasury.application.revenue.handler.LogRevenueCollectionHandler;
import source.treasury.application.revenue.handler.PersistRevenueHandler;
import source.treasury.application.revenue.handler.ValidateProfitabilityHandler;
import source.treasury.domain.model.RevenueCollectionResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectRevenueInteractorTest {

    @Mock
    private RevenuePersistencePort revenuePersistencePort;

    @Mock
    private MerkleLedgerPort merkleLedgerPort;

    @Mock
    private AuditAddressPort auditAddressPort;

    @Test
    void shouldCollectRevenueThroughHandlerChain() {
        when(revenuePersistencePort.accumulateProfit(argThat(
                amount -> amount != null && amount.compareTo(new BigDecimal("0.00001000")) == 0)))
                .thenReturn(new BigDecimal("1.50001000"));
        when(merkleLedgerPort.appendEntry("PROFIT_COLLECTED:1000:0.00001"))
                .thenReturn("root-123");
        when(auditAddressPort.getNextAuditAddress())
                .thenReturn("bc1qtestaudit");

        CollectRevenueInteractor interactor = new CollectRevenueInteractor(
                new ValidateProfitabilityHandler(),
                new PersistRevenueHandler(revenuePersistencePort),
                new AppendMerkleEntryHandler(merkleLedgerPort),
                new AssignAuditAddressHandler(auditAddressPort),
                new LogRevenueCollectionHandler());

        RevenueCollectionResult result = interactor.collectProfit(1_000L, 2_000L);

        assertEquals(1_000L, result.profitSats());
        assertTrue(result.profitBtc().compareTo(new BigDecimal("0.00001000")) == 0);
        assertEquals(new BigDecimal("1.50001000"), result.accumulatedProfitBtc());
        assertEquals("root-123", result.merkleRoot());
        assertEquals("bc1qtestaudit", result.auditAddress());

        verify(revenuePersistencePort).accumulateProfit(argThat(
                amount -> amount != null && amount.compareTo(new BigDecimal("0.00001000")) == 0));
        verify(merkleLedgerPort).appendEntry("PROFIT_COLLECTED:1000:0.00001");
        verify(auditAddressPort).getNextAuditAddress();
    }

    @Test
    void shouldStopChainWhenProfitIsNotPositive() {
        CollectRevenueInteractor interactor = new CollectRevenueInteractor(
                new ValidateProfitabilityHandler(),
                new PersistRevenueHandler(revenuePersistencePort),
                new AppendMerkleEntryHandler(merkleLedgerPort),
                new AssignAuditAddressHandler(auditAddressPort),
                new LogRevenueCollectionHandler());

        RevenueCollectionResult result = interactor.collectProfit(5_000L, 4_000L);

        assertEquals(0L, result.profitSats());
        assertEquals(BigDecimal.ZERO, result.profitBtc());
        assertEquals(BigDecimal.ZERO, result.accumulatedProfitBtc());
        assertNull(result.merkleRoot());
        assertNull(result.auditAddress());

        verifyNoInteractions(revenuePersistencePort, merkleLedgerPort, auditAddressPort);
    }
}
