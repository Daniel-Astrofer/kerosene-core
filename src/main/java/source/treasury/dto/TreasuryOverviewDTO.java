package source.treasury.dto;

import java.math.BigDecimal;

public record TreasuryOverviewDTO(
        BigDecimal totalOnchainBtc,
        BigDecimal lightningNodeBtc,
        BigDecimal inboundLiquidityBtc,
        BigDecimal outboundLiquidityBtc,
        BigDecimal reservedOnchainBtc,
        BigDecimal reservedLightningBtc,
        BigDecimal availableOnchainBtc,
        BigDecimal availableLightningBtc,
        boolean lightningSendsAllowed,
        String liquidityState) {
}
