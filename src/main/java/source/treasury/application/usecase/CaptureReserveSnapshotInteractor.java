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
    private static final long MAX_BITCOIN_SUPPLY_SATS = 21_000_000L * 100_000_000L;
    private static final int MAX_DESCRIPTOR_SCAN_RANGE = 100_000;

    private final BlockchainReservePort blockchainReservePort;
    private final LightningReservePort lightningReservePort;
    private final WalletMonitoringPort walletMonitoringPort;
    private final TreasuryConfigPort treasuryConfigPort;
    private final int walletXpubGapLimit;
    private final int treasuryAuditScanRange;

    public CaptureReserveSnapshotInteractor(
            BlockchainReservePort blockchainReservePort,
            LightningReservePort lightningReservePort,
            WalletMonitoringPort walletMonitoringPort,
            TreasuryConfigPort treasuryConfigPort,
            @Value("${financial.audit.wallet-xpub-gap-limit:20}") int walletXpubGapLimit,
            @Value("${financial.audit.treasury-xpub-scan-range:128}") int treasuryAuditScanRange) {
        this.blockchainReservePort = blockchainReservePort;
        this.lightningReservePort = lightningReservePort;
        this.walletMonitoringPort = walletMonitoringPort;
        this.treasuryConfigPort = treasuryConfigPort;
        this.walletXpubGapLimit = walletXpubGapLimit;
        this.treasuryAuditScanRange = treasuryAuditScanRange;
    }

    @Override
    public ReserveSnapshot captureSnapshot() {
        long hotWalletSats = safeGet(blockchainReservePort::getHotWalletBalance, "hot wallet");
        long lightningNodeSats = safeGet(lightningReservePort::getLightningNodeBalance, "lightning node");

        Set<String> seenXpubs = new HashSet<>();
        Set<String> seenAddresses = new HashSet<>();
        long walletMonitoredSats = 0L;

        for (MonitoredWallet wallet : walletMonitoringPort.findAll()) {
            String xpub = normalize(wallet.xpub());
            if (xpub != null && seenXpubs.add(xpub)) {
                int lastDerived = wallet.lastDerivedIndex() != null ? wallet.lastDerivedIndex() : -1;
                int scanRange = walletScanRange(lastDerived);
                walletMonitoredSats = safeAddSats(
                        walletMonitoredSats,
                        safeGet(
                                () -> blockchainReservePort.getConfirmedBalanceForXpub(xpub, scanRange, true),
                                "wallet xpub " + wallet.id()),
                        "wallet monitored balance");
                continue;
            }

            String depositAddress = normalize(wallet.depositAddress());
            if (depositAddress != null && seenAddresses.add(depositAddress)) {
                walletMonitoredSats = safeAddSats(
                        walletMonitoredSats,
                        safeGet(
                                () -> blockchainReservePort.getConfirmedBalanceForAddress(depositAddress),
                                "deposit address " + depositAddress),
                        "wallet monitored balance");
            }
        }

        long treasuryXpubSats = treasuryConfigPort.loadGlobalConfig()
                .map(config -> normalize(config.auditXpub()))
                .filter(seenXpubs::add)
                .map(xpub -> safeGet(
                        () -> blockchainReservePort.getConfirmedBalanceForXpub(
                                xpub,
                                clampScanRange(treasuryAuditScanRange, "treasury audit scan range"),
                                true),
                        "treasury audit xpub"))
                .orElse(0L);

        // Self-custody XPUB balances are audited separately but must not inflate
        // the platform treasury reserve.
        long totalOnchainSats = safeAddSats(hotWalletSats, treasuryXpubSats, "total on-chain reserve");
        long totalAssetsSats = safeAddSats(totalOnchainSats, lightningNodeSats, "total reserve assets");

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
            return sanitizeSats(supplier.getAsLong(), source);
        } catch (Exception ex) {
            log.warn("[ReserveBalance] Failed to resolve {} balance: {}", source, ex.getMessage());
            return 0L;
        }
    }

    private long sanitizeSats(long value, String source) {
        if (value < 0L) {
            log.warn("[ReserveBalance] Negative {} balance ignored: {}", source, value);
            return 0L;
        }
        if (value > MAX_BITCOIN_SUPPLY_SATS) {
            log.warn("[ReserveBalance] Impossible {} balance ignored: {} sats", source, value);
            return 0L;
        }
        return value;
    }

    private long safeAddSats(long left, long right, String source) {
        try {
            long total = Math.addExact(left, right);
            if (total > MAX_BITCOIN_SUPPLY_SATS) {
                log.warn("[ReserveBalance] Impossible aggregate {} ignored: {} sats", source, total);
                return 0L;
            }
            return total;
        } catch (ArithmeticException exception) {
            log.warn("[ReserveBalance] Overflow while aggregating {}. Returning zero to fail closed.", source);
            return 0L;
        }
    }

    private int walletScanRange(int lastDerivedIndex) {
        int gapLimit = clampScanRange(walletXpubGapLimit, "wallet xpub gap limit");
        long lastDerived = Math.max(-1L, lastDerivedIndex);
        long requested = Math.max((long) gapLimit, lastDerived + 1L + gapLimit);
        if (requested > MAX_DESCRIPTOR_SCAN_RANGE) {
            log.warn("[ReserveBalance] Wallet XPUB scan range clamped from {} to {}", requested, MAX_DESCRIPTOR_SCAN_RANGE);
            return MAX_DESCRIPTOR_SCAN_RANGE;
        }
        return (int) requested;
    }

    private int clampScanRange(int value, String source) {
        if (value < 1) {
            log.warn("[ReserveBalance] {} below minimum; using 1", source);
            return 1;
        }
        if (value > MAX_DESCRIPTOR_SCAN_RANGE) {
            log.warn("[ReserveBalance] {} clamped from {} to {}", source, value, MAX_DESCRIPTOR_SCAN_RANGE);
            return MAX_DESCRIPTOR_SCAN_RANGE;
        }
        return value;
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BITCOIN, 8, RoundingMode.UNNECESSARY);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
