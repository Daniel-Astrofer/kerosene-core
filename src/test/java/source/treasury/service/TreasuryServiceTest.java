package source.treasury.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.ledger.repository.LedgerEntryRepository;
import source.transactions.infra.LightningClient;
import source.transactions.repository.ExternalTransferRepository;
import source.treasury.domain.model.ReserveSnapshot;
import source.treasury.domain.service.LiquidityRebalancePolicy;
import source.treasury.dto.TreasuryOverviewDTO;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasuryServiceTest {

    @Mock
    private ReserveBalanceService reserveBalanceService;

    @Mock
    private LightningClient lightningClient;

    @Mock
    private ExternalTransferRepository externalTransferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LiquidityRebalancePolicy liquidityRebalancePolicy;

    @InjectMocks
    private TreasuryService treasuryService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testOverview_Healthy() {
        ReserveSnapshot snapshot = new ReserveSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("1.0"), new BigDecimal("1.5"));
        when(reserveBalanceService.captureSnapshot()).thenReturn(snapshot);
        when(lightningClient.getLocalBalance()).thenReturn(50_000_000L); // 0.5 BTC
        when(lightningClient.getRemoteBalance()).thenReturn(20_000_000L); // 0.2 BTC
        
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(eq("ONCHAIN"), any()))
                .thenReturn(new BigDecimal("0.1"));
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(eq("LIGHTNING"), any()))
                .thenReturn(new BigDecimal("0.05"));
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(BigDecimal.ZERO);
        when(externalTransferRepository.sumUnsettledPlatformFeesByStatuses(any())).thenReturn(BigDecimal.ZERO);
                
        when(liquidityRebalancePolicy.requiresLoopOut(50_000_000L, 20_000_000L)).thenReturn(false);

        TreasuryOverviewDTO dto = treasuryService.overview();

        assertTrue(dto.lightningSendsAllowed());
        assertEquals("HEALTHY", dto.liquidityState());
        assertEquals(new BigDecimal("0.90000000"), dto.availableOnchainBtc());
        assertEquals(new BigDecimal("0.45000000"), dto.availableLightningBtc());
    }

    @Test
    void testOverview_BlockedOnchainReserve() {
        ReserveSnapshot snapshot = new ReserveSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("0.1"), new BigDecimal("0.6"));
        when(reserveBalanceService.captureSnapshot()).thenReturn(snapshot);
        when(lightningClient.getLocalBalance()).thenReturn(50_000_000L); // 0.5 BTC
        when(lightningClient.getRemoteBalance()).thenReturn(20_000_000L);
        
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(BigDecimal.ZERO);
        when(externalTransferRepository.sumUnsettledPlatformFeesByStatuses(any())).thenReturn(BigDecimal.ZERO);

        TreasuryOverviewDTO dto = treasuryService.overview();

        assertFalse(dto.lightningSendsAllowed());
        assertEquals("BLOCKED_ONCHAIN_RESERVE", dto.liquidityState());
    }

    @Test
    void testAssertLightningOutboundAvailable_Success() {
        ReserveSnapshot snapshot = new ReserveSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("1.0"), new BigDecimal("1.5"));
        when(reserveBalanceService.captureSnapshot()).thenReturn(snapshot);
        when(lightningClient.getLocalBalance()).thenReturn(50_000_000L); // 0.5 BTC
        when(lightningClient.getRemoteBalance()).thenReturn(20_000_000L); // 0.2 BTC
        
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(BigDecimal.ZERO);
        when(externalTransferRepository.sumUnsettledPlatformFeesByStatuses(any())).thenReturn(BigDecimal.ZERO);
                
        when(liquidityRebalancePolicy.requiresLoopOut(50_000_000L, 20_000_000L)).thenReturn(false);

        assertDoesNotThrow(() -> treasuryService.assertLightningOutboundAvailable(10_000_000L));
    }

    @Test
    void testAssertLightningOutboundAvailable_ThrowsInsufficient() {
        ReserveSnapshot snapshot = new ReserveSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("1.0"), new BigDecimal("1.5"));
        when(reserveBalanceService.captureSnapshot()).thenReturn(snapshot);
        when(lightningClient.getLocalBalance()).thenReturn(5_000_000L); // 0.05 BTC
        when(lightningClient.getRemoteBalance()).thenReturn(20_000_000L);
        
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(BigDecimal.ZERO);
        when(externalTransferRepository.sumUnsettledPlatformFeesByStatuses(any())).thenReturn(BigDecimal.ZERO);

        Exception ex = assertThrows(IllegalStateException.class, 
            () -> treasuryService.assertLightningOutboundAvailable(10_000_000L));
        assertEquals("Insufficient outbound Lightning liquidity for this payment.", ex.getMessage());
    }

    @Test
    void assertLightningOutboundAvailableRejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> treasuryService.assertLightningOutboundAvailable(0L));
    }

    @Test
    void testOverview_IsolatesPlatformFeesFromOnchainAvailability() {
        ReserveSnapshot snapshot = new ReserveSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.2"), new BigDecimal("1.0"), new BigDecimal("1.2"));
        when(reserveBalanceService.captureSnapshot()).thenReturn(snapshot);
        when(lightningClient.getLocalBalance()).thenReturn(20_000_000L);
        when(lightningClient.getRemoteBalance()).thenReturn(20_000_000L);
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(eq("ONCHAIN"), any()))
                .thenReturn(new BigDecimal("0.30000000"));
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(eq("LIGHTNING"), any()))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(new BigDecimal("0.10000000"));
        when(externalTransferRepository.sumUnsettledPlatformFeesByStatuses(any())).thenReturn(new BigDecimal("0.05000000"));
        when(liquidityRebalancePolicy.requiresLoopOut(20_000_000L, 20_000_000L)).thenReturn(false);

        TreasuryOverviewDTO dto = treasuryService.overview();

        assertEquals(new BigDecimal("0.55000000"), dto.availableOnchainBtc());
        assertEquals(new BigDecimal("0.20000000"), dto.availableLightningBtc());
        assertTrue(dto.lightningSendsAllowed());
    }

    @Test
    void overviewFloorsAssetsCeilsObligationsAndIgnoresNegativeReservations() {
        ReserveSnapshot snapshot = new ReserveSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("1.000000009"), new BigDecimal("1.000000009"));
        when(reserveBalanceService.captureSnapshot()).thenReturn(snapshot);
        when(lightningClient.getLocalBalance()).thenReturn(10_000_000L);
        when(lightningClient.getRemoteBalance()).thenReturn(10_000_000L);
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(eq("ONCHAIN"), any()))
                .thenReturn(new BigDecimal("0.000000001"));
        when(externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(eq("LIGHTNING"), any()))
                .thenReturn(new BigDecimal("-0.50000000"));
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(new BigDecimal("-0.10000000"));
        when(externalTransferRepository.sumUnsettledPlatformFeesByStatuses(any()))
                .thenReturn(new BigDecimal("0.000000001"));
        when(liquidityRebalancePolicy.requiresLoopOut(10_000_000L, 10_000_000L)).thenReturn(false);

        TreasuryOverviewDTO dto = treasuryService.overview();

        assertEquals(new BigDecimal("1.00000000"), dto.totalOnchainBtc());
        assertEquals(new BigDecimal("0.00000001"), dto.reservedOnchainBtc());
        assertEquals(new BigDecimal("0.00000000"), dto.reservedLightningBtc());
        assertEquals(new BigDecimal("0.99999998"), dto.availableOnchainBtc());
    }
}
