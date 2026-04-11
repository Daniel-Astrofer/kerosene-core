package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.LightningClient;
import source.treasury.entity.TreasuryConfig;
import source.treasury.repository.TreasuryConfigRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReserveBalanceService {

    private static final Logger log = LoggerFactory.getLogger(ReserveBalanceService.class);

    private final BlockchainClient blockchainClient;
    private final LightningClient lightningClient;
    private final WalletRepository walletRepository;
    private final TreasuryConfigRepository treasuryConfigRepository;
    private final int walletXpubGapLimit;
    private final int treasuryAuditScanRange;
    private final boolean bitcoinMockMode;

    public ReserveBalanceService(
            BlockchainClient blockchainClient,
            LightningClient lightningClient,
            WalletRepository walletRepository,
            TreasuryConfigRepository treasuryConfigRepository,
            @Value("${financial.audit.wallet-xpub-gap-limit:20}") int walletXpubGapLimit,
            @Value("${financial.audit.treasury-xpub-scan-range:128}") int treasuryAuditScanRange,
            @Value("${bitcoin.mock-mode:false}") boolean bitcoinMockMode) {
        this.blockchainClient = blockchainClient;
        this.lightningClient = lightningClient;
        this.walletRepository = walletRepository;
        this.treasuryConfigRepository = treasuryConfigRepository;
        this.walletXpubGapLimit = walletXpubGapLimit;
        this.treasuryAuditScanRange = treasuryAuditScanRange;
        this.bitcoinMockMode = bitcoinMockMode;
    }

    public ReserveSnapshot captureSnapshot() {
        long hotWalletSats = bitcoinMockMode ? 0L : safeGet(blockchainClient::getHotWalletBalance, "hot wallet");
        long lightningNodeSats = safeGet(lightningClient::getLightningNodeBalance, "lightning node");

        Set<String> seenXpubs = new HashSet<>();
        Set<String> seenAddresses = new HashSet<>();
        long walletMonitoredSats = 0L;

        List<WalletEntity> wallets = walletRepository.findAll();
        for (WalletEntity wallet : wallets) {
            String xpub = normalize(wallet.getXpub());
            if (xpub != null && seenXpubs.add(xpub)) {
                int lastDerived = wallet.getLastDerivedIndex() != null ? wallet.getLastDerivedIndex() : -1;
                int scanRange = Math.max(walletXpubGapLimit, lastDerived + 1 + walletXpubGapLimit);
                long balance = safeGet(() -> blockchainClient.getConfirmedBalanceForXpub(xpub, scanRange, true),
                        "wallet xpub " + wallet.getId());
                walletMonitoredSats += balance;
                continue;
            }

            String depositAddress = normalize(wallet.getDepositAddress());
            if (depositAddress != null && seenAddresses.add(depositAddress)) {
                walletMonitoredSats += safeGet(() -> blockchainClient.getConfirmedBalanceForAddress(depositAddress),
                        "deposit address " + depositAddress);
            }
        }

        long treasuryXpubSats = treasuryConfigRepository.getGlobalConfig()
                .map(TreasuryConfig::getAuditXpub)
                .map(this::normalize)
                .filter(seenXpubs::add)
                .map(xpub -> safeGet(
                        () -> blockchainClient.getConfirmedBalanceForXpub(xpub, treasuryAuditScanRange, true),
                        "treasury audit xpub"))
                .orElse(0L);

        long totalOnchainSats = safeAdd(hotWalletSats, walletMonitoredSats, treasuryXpubSats);
        long totalAssetsSats = safeAdd(totalOnchainSats, lightningNodeSats);

        return new ReserveSnapshot(
                satsToBtc(hotWalletSats),
                satsToBtc(walletMonitoredSats),
                satsToBtc(treasuryXpubSats),
                satsToBtc(lightningNodeSats),
                satsToBtc(totalOnchainSats),
                satsToBtc(totalAssetsSats));
    }

    public record ReserveSnapshot(
            BigDecimal hotWalletBtc,
            BigDecimal walletMonitoredOnchainBtc,
            BigDecimal treasuryXpubOnchainBtc,
            BigDecimal lightningBtc,
            BigDecimal totalOnchainBtc,
            BigDecimal totalAssetsBtc) {

        public long totalAssetsSats() {
            return btcToSats(totalAssetsBtc);
        }

        public long totalOnchainSats() {
            return btcToSats(totalOnchainBtc);
        }

        private static long btcToSats(BigDecimal value) {
            if (value == null) {
                return 0L;
            }
            return value.multiply(new BigDecimal("100000000"))
                    .setScale(0, RoundingMode.DOWN)
                    .longValue();
        }
    }

    private long safeGet(java.util.function.LongSupplier supplier, String source) {
        try {
            return Math.max(0L, supplier.getAsLong());
        } catch (Exception ex) {
            log.warn("[ReserveBalance] Failed to resolve {} balance: {}", source, ex.getMessage());
            return 0L;
        }
    }

    private long safeAdd(long... values) {
        long total = 0L;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
