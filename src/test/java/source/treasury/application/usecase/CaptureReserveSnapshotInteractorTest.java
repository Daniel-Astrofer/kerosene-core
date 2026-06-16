package source.treasury.application.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.treasury.application.port.out.BlockchainReservePort;
import source.treasury.application.port.out.LightningReservePort;
import source.treasury.application.port.out.TreasuryConfigPort;
import source.treasury.application.port.out.WalletMonitoringPort;
import source.treasury.domain.model.MonitoredWallet;
import source.treasury.domain.model.ReserveSnapshot;
import source.treasury.domain.model.TreasuryConfigState;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptureReserveSnapshotInteractorTest {

    private static final long MAX_BITCOIN_SUPPLY_SATS = 21_000_000L * 100_000_000L;

    @Mock
    private BlockchainReservePort blockchainReservePort;

    @Mock
    private LightningReservePort lightningReservePort;

    @Mock
    private WalletMonitoringPort walletMonitoringPort;

    @Mock
    private TreasuryConfigPort treasuryConfigPort;

    @Test
    void shouldAggregateReserveSourcesWithoutDuplicatingWalletXpubs() {
        when(blockchainReservePort.getHotWalletBalance()).thenReturn(100L);
        when(lightningReservePort.getLightningNodeBalance()).thenReturn(50L);
        when(walletMonitoringPort.findAll()).thenReturn(List.of(
                new MonitoredWallet(1L, "xpub-1", 3, null),
                new MonitoredWallet(2L, "xpub-1", 8, null),
                new MonitoredWallet(3L, null, null, "bc1-wallet-deposit")));
        when(blockchainReservePort.getConfirmedBalanceForXpub("xpub-1", 24, true)).thenReturn(200L);
        when(blockchainReservePort.getConfirmedBalanceForAddress("bc1-wallet-deposit")).thenReturn(300L);
        when(treasuryConfigPort.loadGlobalConfig()).thenReturn(Optional.of(
                new TreasuryConfigState(BigDecimal.ONE, "xpub-treasury", null)));
        when(blockchainReservePort.getConfirmedBalanceForXpub("xpub-treasury", 128, true)).thenReturn(400L);

        CaptureReserveSnapshotInteractor interactor = new CaptureReserveSnapshotInteractor(
                blockchainReservePort,
                lightningReservePort,
                walletMonitoringPort,
                treasuryConfigPort,
                20,
                128);

        ReserveSnapshot snapshot = interactor.captureSnapshot();

        assertEquals(new BigDecimal("0.00000100"), snapshot.hotWalletBtc());
        assertEquals(new BigDecimal("0.00000500"), snapshot.walletMonitoredOnchainBtc());
        assertEquals(new BigDecimal("0.00000400"), snapshot.treasuryXpubOnchainBtc());
        assertEquals(new BigDecimal("0.00000050"), snapshot.lightningBtc());
        assertEquals(new BigDecimal("0.00000500"), snapshot.totalOnchainBtc());
        assertEquals(new BigDecimal("0.00000550"), snapshot.totalAssetsBtc());

        verify(blockchainReservePort).getConfirmedBalanceForXpub("xpub-1", 24, true);
    }

    @Test
    void shouldFailClosedForImpossibleBalancesAndClampScanRanges() {
        when(blockchainReservePort.getHotWalletBalance()).thenReturn(MAX_BITCOIN_SUPPLY_SATS + 1L);
        when(lightningReservePort.getLightningNodeBalance()).thenReturn(-1L);
        when(walletMonitoringPort.findAll()).thenReturn(List.of(
                new MonitoredWallet(1L, "xpub-large-range", Integer.MAX_VALUE, null)));
        when(blockchainReservePort.getConfirmedBalanceForXpub("xpub-large-range", 100_000, true))
                .thenReturn(42L);
        when(treasuryConfigPort.loadGlobalConfig()).thenReturn(Optional.of(
                new TreasuryConfigState(BigDecimal.ONE, "xpub-treasury", null)));
        when(blockchainReservePort.getConfirmedBalanceForXpub("xpub-treasury", 100_000, true))
                .thenReturn(MAX_BITCOIN_SUPPLY_SATS + 1L);

        CaptureReserveSnapshotInteractor interactor = new CaptureReserveSnapshotInteractor(
                blockchainReservePort,
                lightningReservePort,
                walletMonitoringPort,
                treasuryConfigPort,
                250_000,
                250_000);

        ReserveSnapshot snapshot = interactor.captureSnapshot();

        assertEquals(new BigDecimal("0.00000000"), snapshot.hotWalletBtc());
        assertEquals(new BigDecimal("0.00000042"), snapshot.walletMonitoredOnchainBtc());
        assertEquals(new BigDecimal("0.00000000"), snapshot.treasuryXpubOnchainBtc());
        assertEquals(new BigDecimal("0.00000000"), snapshot.totalOnchainBtc());
        assertEquals(new BigDecimal("0.00000000"), snapshot.totalAssetsBtc());
        verify(blockchainReservePort).getConfirmedBalanceForXpub("xpub-large-range", 100_000, true);
        verify(blockchainReservePort).getConfirmedBalanceForXpub("xpub-treasury", 100_000, true);
    }
}
