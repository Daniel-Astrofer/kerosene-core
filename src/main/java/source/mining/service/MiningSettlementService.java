package source.mining.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.kfe.model.KfeBalanceEntity;
import source.kfe.service.KfeBalanceService;
import source.mining.entity.MiningAllocationEntity;
import source.mining.entity.MiningRigOfferEntity;
import source.mining.exception.MiningExceptions;
import source.mining.repository.MiningAllocationRepository;
import source.notification.l10n.NotificationMessageKey;
import source.notification.l10n.NotificationMessages;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import java.util.UUID;

@Service
public class MiningSettlementService {

    private static final Logger log = LoggerFactory.getLogger(MiningSettlementService.class);

    private final KfeBalanceService balanceService;
    private final RigCatalog rigCatalog;
    private final MiningAllocationRepository allocationRepository;
    private final MiningHistoryPort historyPort;
    private final NotificationService notificationService;

    public MiningSettlementService(
            KfeBalanceService balanceService,
            RigCatalog rigCatalog,
            MiningAllocationRepository allocationRepository,
            MiningHistoryPort historyPort,
            NotificationService notificationService) {
        this.balanceService = balanceService;
        this.rigCatalog = rigCatalog;
        this.allocationRepository = allocationRepository;
        this.historyPort = historyPort;
        this.notificationService = notificationService;
    }

    public void ensureBalance(UUID walletId, BigDecimal requiredAmount) {
        KfeBalanceEntity balance = balanceService.requireForUpdate(walletId, "BTC");
        long requiredSats = btcToSats(requiredAmount);
        if (balance.getAvailableSats() < requiredSats) {
            throw new IllegalStateException(
                    "Insufficient wallet balance for the requested mining allocation.");
        }
    }

    public void debitRental(UUID walletId, BigDecimal rentalCost, String rigCode) {
        long amountSats = btcToSats(rentalCost);
        balanceService.reserve(walletId, "BTC", amountSats);
        balanceService.settleReservedDebit(walletId, "BTC", amountSats);
    }

    @Transactional
    public void settleIfDue(MiningAllocationEntity allocation) {
        if (!"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
            return;
        }
        if (allocation.getEndsAt() == null || allocation.getEndsAt().isAfter(LocalDateTime.now())) {
            return;
        }

        balanceService.creditAvailable(
                allocation.getWalletId(),
                "BTC",
                btcToSats(allocation.getProjectedNetYieldBtc()));

        allocation.setStatus("COMPLETED");
        allocation.setSettledAt(LocalDateTime.now());
        allocationRepository.save(allocation);

        historyPort.record(new MiningHistoryPort.MiningHistoryRecord(
                allocation.getUserId(),
                allocation.getRigNameSnapshot(),
                allocation.getWalletNameSnapshot(),
                "MINING_PAYOUT_SETTLEMENT",
                allocation.getProjectedNetYieldBtc(),
                "COMPLETED",
                allocation.getProviderRentalReference(),
                "Mining allocation settled and proceeds credited.",
                LocalDateTime.now()));
        notifyUser(
                allocation.getUserId(),
                NotificationKind.MINING_COMPLETED,
                NotificationSeverity.SUCCESS,
                NotificationMessageKey.MINING_COMPLETED,
                "/mining",
                "mining_allocation",
                allocation.getId() != null ? allocation.getId().toString() : null,
                Map.of(
                        "walletName", allocation.getWalletNameSnapshot(),
                        "rigName", allocation.getRigNameSnapshot(),
                        "amountBtc", allocation.getProjectedNetYieldBtc().toPlainString()));
    }

    @Transactional
    public MiningAllocationEntity cancel(Long userId, MiningAllocationEntity allocation) {
        if (!"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
            throw new MiningExceptions.MiningAllocationStateException(
                    "Only active allocations can be cancelled.");
        }

        MiningRigOfferEntity rig = rigCatalog.getRig(allocation.getRigId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = allocation.getEndsAt();
        LocalDateTime start = allocation.getStartsAt();
        long totalMinutes = Math.max(1L, Duration.between(start, end).toMinutes());
        long elapsedMinutes = Math.max(0L, Math.min(totalMinutes, Duration.between(start, now).toMinutes()));
        BigDecimal elapsedRatio = new BigDecimal(elapsedMinutes)
                .divide(new BigDecimal(totalMinutes), 8, RoundingMode.HALF_UP);
        BigDecimal remainingRatio = BigDecimal.ONE.subtract(elapsedRatio).max(BigDecimal.ZERO);

        BigDecimal minedToDate = normalize(allocation.getProjectedNetYieldBtc().multiply(elapsedRatio));
        BigDecimal refundable = normalize(allocation.getRentalCostBtc().multiply(remainingRatio));

        BigDecimal creditBack = minedToDate.add(refundable).setScale(8, RoundingMode.HALF_UP);
        if (creditBack.compareTo(BigDecimal.ZERO) > 0) {
            balanceService.creditAvailable(allocation.getWalletId(), "BTC", btcToSats(creditBack));
        }

        rigCatalog.releaseHashrate(rig, allocation.getAllocatedHashrate());

        allocation.setRefundedAmountBtc(refundable);
        allocation.setSettledAt(now);
        allocation.setStatus("CANCELLED");
        MiningAllocationEntity saved = allocationRepository.save(allocation);

        historyPort.record(new MiningHistoryPort.MiningHistoryRecord(
                userId,
                allocation.getWalletNameSnapshot(),
                allocation.getRigNameSnapshot(),
                "MINING_HASHPOWER_CANCEL",
                creditBack,
                "CANCELLED",
                allocation.getProviderRentalReference(),
                "Mining allocation cancelled with pro-rated refund.",
                LocalDateTime.now()));
        notifyUser(
                userId,
                NotificationKind.MINING_CANCELLED,
                NotificationSeverity.WARNING,
                NotificationMessageKey.MINING_CANCELLED,
                "/mining",
                "mining_allocation",
                allocation.getId() != null ? allocation.getId().toString() : null,
                Map.of(
                        "walletName", allocation.getWalletNameSnapshot(),
                        "rigName", allocation.getRigNameSnapshot(),
                        "amountBtc", creditBack.toPlainString()));

        return saved;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private long btcToSats(BigDecimal value) {
        return normalize(value).movePointRight(8).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private void notifyUser(
            Long userId,
            NotificationKind kind,
            NotificationSeverity severity,
            NotificationMessageKey messageKey,
            String deeplink,
            String entityType,
            String entityId,
            Map<String, String> metadata,
            Object... args) {
        try {
            notificationService.notifyUser(
                    userId,
                    NotificationMessages.payload(
                            kind,
                            severity,
                            messageKey,
                            deeplink,
                            entityType,
                            entityId,
                            metadata,
                            args));
        } catch (Exception ex) {
            log.warn("Failed to emit mining notification: {}", ex.getMessage());
        }
    }
}
