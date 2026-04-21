package source.treasury.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.treasury.application.port.in.CaptureReserveSnapshotUseCase;
import source.treasury.application.port.out.BlockchainReservePort;
import source.treasury.application.port.out.LightningReservePort;
import source.treasury.application.port.out.TreasuryConfigPort;
import source.treasury.application.port.out.WalletMonitoringPort;
import source.treasury.domain.model.MonitoredWallet;
import source.treasury.domain.model.ReserveSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

@Service
public class CaptureReserveSnapshotInteractor implements CaptureReserveSnapshotUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureReserveSnapshotInteractor.class);
    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");

    private final BlockchainReservePort blockchainReservePort;
    private final LightningReservePort lightningReservePort;
    private final WalletMonitoringPort walletMonitoringPort;
    private final TreasuryConfigPort treasuryConfigPort;
    private final int walletXpubGapLimit;
    private final int treasuryAuditScanRange;
    private final boolean bitcoinMockMode;

    public CaptureReserveSnapshotInteractor(
            BlockchainReservePort blockchainReservePort,
            LightningReservePort lightningReservePort,
            WalletMonitoringPort walletMonitoringPort,
            TreasuryConfigPort treasuryConfigPort,
            @Value("${financial.audit.wallet-xpub-gap-limit:20}") int walletXpubGapLimit,
            @Value("${financial.audit.treasury-xpub-scan-range:128}") int treasuryAuditScanRange,
            @Value("${bitcoin.mock-mode:false}") boolean bitcoinMockMode) {
        this.blockchainReservePort = blockchainReservePort;
        this.lightningReservePort = lightningReservePort;
        this.walletMonitoringPort = walletMonitoringPort;
        this.treasuryConfigPort = treasuryConfigPort;
        this.walletXpubGapLimit = walletXpubGapLimit;
        this.treasuryAuditScanRange = treasuryAuditScanRange;
        this.bitcoinMockMode = bitcoinMockMode;
    }

    @Override
    public ReserveSnapshot captureSnapshot() {
        long hotWalletSats = bitcoinMockMode ? 0L : safeGet(blockchainReservePort::getHotWalletBalance, "hot wallet");
        long lightningNodeSats = safeGet(lightningReservePort::getLightningNodeBalance, "lightning node");

        Set<String> seenXpubs = new HashSet<>();
        Set<String> seenAddresses = new HashSet<>();
        long walletMonitoredSats = 0L;

        for (MonitoredWallet wallet : walletMonitoringPort.findAll()) {
            String xpub = normalize(wallet.xpub());
            if (xpub != null && seenXpubs.add(xpub)) {
                int lastDerived = wallet.lastDerivedIndex() != null ? wallet.lastDerivedIndex() : -1;
                int scanRange = Math.max(walletXpubGapLimit, lastDerived + 1 + walletXpubGapLimit);
                walletMonitoredSats += safeGet(
                        () -> blockchainReservePort.getConfirmedBalanceForXpub(xpub, scanRange, true),
                        "wallet xpub " + wallet.id());
                continue;
            }

            String depositAddress = normalize(wallet.depositAddress());
            if (depositAddress != null && seenAddresses.add(depositAddress)) {
                walletMonitoredSats += safeGet(
                        () -> blockchainReservePort.getConfirmedBalanceForAddress(depositAddress),
                        "deposit address " + depositAddress);
            }
        }

        long treasuryXpubSats = treasuryConfigPort.loadGlobalConfig()
                .map(config -> normalize(config.auditXpub()))
                .filter(seenXpubs::add)
                .map(xpub -> safeGet(
                        () -> blockchainReservePort.getConfirmedBalanceForXpub(xpub, treasuryAuditScanRange, true),
                        "treasury audit xpub"))
                .orElse(0L);

        long totalOnchainSats = hotWalletSats + walletMonitoredSats + treasuryXpubSats;
        long totalAssetsSats = totalOnchainSats + lightningNodeSats;

        return new ReserveSnapshot(
                satsToBtc(hotWalletSats),
                satsToBtc(walletMonitoredSats),
                satsToBtc(treasuryXpubSats),
                satsToBtc(lightningNodeSats),
                satsToBtc(totalOnchainSats),
                satsToBtc(totalAssetsSats));
    }

    private long safeGet(java.util.function.LongSupplier supplier, String source) {
        try {
            return Math.max(0L, supplier.getAsLong());
        } catch (Exception ex) {
            log.warn("[ReserveBalance] Failed to resolve {} balance: {}", source, ex.getMessage());
            return 0L;
        }
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BITCOIN, 8, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
