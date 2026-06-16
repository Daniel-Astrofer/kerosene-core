package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.ledger.repository.LedgerEntryRepository;
import source.transactions.infra.LightningClient;
import source.transactions.repository.ExternalTransferRepository;
import source.treasury.domain.service.LiquidityRebalancePolicy;
import source.treasury.dto.TreasuryOverviewDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TreasuryService {

    private static final Logger log = LoggerFactory.getLogger(TreasuryService.class);
    private static final int BTC_SCALE = 8;
    private static final BigDecimal SATOSHIS_PER_BITCOIN = new BigDecimal("100000000");
    private static final BigDecimal ZERO_BTC = BigDecimal.ZERO.setScale(BTC_SCALE, RoundingMode.UNNECESSARY);
    private static final List<String> RESERVED_STATUSES = List.of(
            "PENDING",
            "PROVIDER_PENDING",
            "MEMPOOL",
            "CONFIRMED",
            "AUTO_RESOLUTION_PENDING");
    private static final List<String> UNSETTLED_FEE_STATUSES = List.of(
            "PENDING",
            "PROVIDER_PENDING",
            "AUTO_RESOLUTION_PENDING");

    private final ReserveBalanceService reserveBalanceService;
    private final LightningClient lightningClient;
    private final ExternalTransferRepository externalTransferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LiquidityRebalancePolicy liquidityRebalancePolicy;

    public TreasuryService(
            ReserveBalanceService reserveBalanceService,
            @Qualifier("lndLightningGateway") LightningClient lightningClient,
            ExternalTransferRepository externalTransferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LiquidityRebalancePolicy liquidityRebalancePolicy) {
        this.reserveBalanceService = reserveBalanceService;
        this.lightningClient = lightningClient;
        this.externalTransferRepository = externalTransferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.liquidityRebalancePolicy = liquidityRebalancePolicy;
    }

    public TreasuryOverviewDTO overview() {
        var snapshot = reserveBalanceService.captureSnapshot();

        long outboundLiquiditySats = Math.max(0L, lightningClient.getLocalBalance());
        long inboundLiquiditySats = Math.max(0L, lightningClient.getRemoteBalance());
        BigDecimal totalOnchainBtc = normalizeAsset(snapshot.totalOnchainBtc(), "total on-chain reserve");
        BigDecimal reservedOnchainBtc = normalizeObligation(
                externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(
                        "ONCHAIN", RESERVED_STATUSES),
                "reserved on-chain rail outflow");
        BigDecimal reservedLightningBtc = normalizeObligation(
                externalTransferRepository.sumProjectedOutboundRailOutflowByNetworkAndStatuses(
                        "LIGHTNING", RESERVED_STATUSES),
                "reserved Lightning rail outflow");
        BigDecimal isolatedPlatformFeesBtc = normalizeObligation(
                normalizeObligation(ledgerEntryRepository.calculatePlatformProfitPending(), "pending platform profit")
                        .add(normalizeObligation(
                                externalTransferRepository.sumUnsettledPlatformFeesByStatuses(UNSETTLED_FEE_STATUSES),
                                "unsettled platform fees")),
                "isolated platform fees");

        BigDecimal availableOnchainBtc = nonNegativeAvailable(totalOnchainBtc
                .subtract(isolatedPlatformFeesBtc)
                .subtract(reservedOnchainBtc));
        BigDecimal availableLightningBtc = nonNegativeAvailable(satsToBtc(outboundLiquiditySats)
                .subtract(reservedLightningBtc));

        BigDecimal outboundLiquidityBtc = satsToBtc(outboundLiquiditySats);
        BigDecimal projectedLightningLiquidityBtc = nonNegativeAvailable(outboundLiquidityBtc
                .subtract(reservedLightningBtc));
        boolean reserveBacksLightning = availableOnchainBtc.compareTo(projectedLightningLiquidityBtc) >= 0;
        boolean healthyInbound = !liquidityRebalancePolicy.requiresLoopOut(outboundLiquiditySats, inboundLiquiditySats);
        boolean lightningAllowed = reserveBacksLightning && availableLightningBtc.signum() > 0;
        String liquidityState = !reserveBacksLightning
                ? "BLOCKED_ONCHAIN_RESERVE"
                : (!healthyInbound ? "REBALANCE_REQUIRED" : "HEALTHY");

        return new TreasuryOverviewDTO(
                totalOnchainBtc,
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
        if (amountSats <= 0L) {
            throw new IllegalArgumentException("Lightning payment amount must be positive.");
        }
        TreasuryOverviewDTO overview = overview();
        BigDecimal requested = satsToBtc(amountSats);
        if (!overview.lightningSendsAllowed()) {
            throw new IllegalStateException("Lightning sends are temporarily blocked due to treasury reserve mismatch.");
        }
        if (overview.availableLightningBtc().compareTo(requested) < 0) {
            throw new IllegalStateException("Insufficient outbound Lightning liquidity for this payment.");
        }
    }

    private BigDecimal normalizeAsset(BigDecimal value, String source) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        if (safeValue.signum() < 0) {
            log.warn("[Treasury] Negative {} ignored while calculating availability.", source);
            return ZERO_BTC;
        }
        return safeValue.setScale(BTC_SCALE, RoundingMode.DOWN);
    }

    private BigDecimal normalizeObligation(BigDecimal value, String source) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        if (safeValue.signum() < 0) {
            log.warn("[Treasury] Negative {} ignored while calculating reserved obligations.", source);
            return ZERO_BTC;
        }
        return safeValue.setScale(BTC_SCALE, RoundingMode.CEILING);
    }

    private BigDecimal satsToBtc(long sats) {
        return new BigDecimal(sats).divide(SATOSHIS_PER_BITCOIN, BTC_SCALE, RoundingMode.UNNECESSARY);
    }

    private BigDecimal nonNegativeAvailable(BigDecimal value) {
        return value.signum() >= 0 ? normalizeAsset(value, "available balance") : ZERO_BTC;
    }
}
