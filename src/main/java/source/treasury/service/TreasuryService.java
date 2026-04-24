package source.treasury.service;

import org.springframework.stereotype.Service;
import source.transactions.infra.LightningClient;
import source.transactions.repository.ExternalTransferRepository;
import source.treasury.domain.service.LiquidityRebalancePolicy;
import source.treasury.dto.TreasuryOverviewDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TreasuryService {

    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");
    private static final List<String> RESERVED_STATUSES = List.of("PENDING", "MEMPOOL", "CONFIRMED");

    private final ReserveBalanceService reserveBalanceService;
    private final LightningClient lightningClient;
    private final ExternalTransferRepository externalTransferRepository;
    private final LiquidityRebalancePolicy liquidityRebalancePolicy;

    public TreasuryService(
            ReserveBalanceService reserveBalanceService,
            LightningClient lightningClient,
            ExternalTransferRepository externalTransferRepository,
            LiquidityRebalancePolicy liquidityRebalancePolicy) {
        this.reserveBalanceService = reserveBalanceService;
        this.lightningClient = lightningClient;
        this.externalTransferRepository = externalTransferRepository;
        this.liquidityRebalancePolicy = liquidityRebalancePolicy;
    }

    public TreasuryOverviewDTO overview() {
        var snapshot = reserveBalanceService.captureSnapshot();

        long outboundLiquiditySats = Math.max(0L, lightningClient.getLocalBalance());
        long inboundLiquiditySats = Math.max(0L, lightningClient.getRemoteBalance());
        BigDecimal reservedOnchainBtc = normalize(
                externalTransferRepository.sumReservedOutboundByNetworkAndStatuses("ONCHAIN", RESERVED_STATUSES));
        BigDecimal reservedLightningBtc = normalize(
                externalTransferRepository.sumReservedOutboundByNetworkAndStatuses("LIGHTNING", RESERVED_STATUSES));

        BigDecimal availableOnchainBtc = nonNegative(snapshot.totalOnchainBtc().subtract(reservedOnchainBtc));
        BigDecimal availableLightningBtc = nonNegative(satsToBtc(outboundLiquiditySats).subtract(reservedLightningBtc));

        BigDecimal outboundLiquidityBtc = satsToBtc(outboundLiquiditySats);
        boolean reserveBacksLightning = snapshot.totalOnchainBtc().compareTo(outboundLiquidityBtc) >= 0;
        boolean healthyInbound = !liquidityRebalancePolicy.requiresLoopOut(outboundLiquiditySats, inboundLiquiditySats);
        boolean lightningAllowed = reserveBacksLightning && availableLightningBtc.signum() > 0;
        String liquidityState = !reserveBacksLightning
                ? "BLOCKED_ONCHAIN_RESERVE"
                : (!healthyInbound ? "REBALANCE_REQUIRED" : "HEALTHY");

        return new TreasuryOverviewDTO(
                snapshot.totalOnchainBtc(),
                snapshot.lightningBtc(),
                satsToBtc(inboundLiquiditySats),
                satsToBtc(outboundLiquiditySats),
                reservedOnchainBtc,
                reservedLightningBtc,
                availableOnchainBtc,
                availableLightningBtc,
                lightningAllowed,
                liquidityState);
    }

    public void assertLightningOutboundAvailable(long amountSats) {
        TreasuryOverviewDTO overview = overview();
        BigDecimal requested = satsToBtc(amountSats);
        if (!overview.lightningSendsAllowed()) {
            throw new IllegalStateException("Lightning sends are temporarily blocked due to treasury reserve mismatch.");
        }
        if (overview.availableLightningBtc().compareTo(requested) < 0) {
            throw new IllegalStateException("Insufficient outbound Lightning liquidity for this payment.");
        }
    }

    private BigDecimal normalize(BigDecimal value) {
        return value != null ? value.setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BITCOIN, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.signum() >= 0 ? normalize(value) : BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
    }
}
