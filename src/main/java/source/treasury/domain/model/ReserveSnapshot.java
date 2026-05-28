package source.treasury.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
