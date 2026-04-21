package source.mining.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerService;
import source.mining.entity.MiningAllocationEntity;
import source.mining.entity.MiningRigOfferEntity;
import source.mining.exception.MiningExceptions;
import source.mining.repository.MiningAllocationRepository;
import source.notification.service.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class MiningSettlementService {

    private static final Logger log = LoggerFactory.getLogger(MiningSettlementService.class);

    private final LedgerService ledgerService;
    private final RigCatalog rigCatalog;
    private final MiningAllocationRepository allocationRepository;
    private final MiningHistoryPort historyPort;
    private final NotificationService notificationService;

    public MiningSettlementService(
            LedgerService ledgerService,
            RigCatalog rigCatalog,
            MiningAllocationRepository allocationRepository,
            MiningHistoryPort historyPort,
            NotificationService notificationService) {
        this.ledgerService = ledgerService;
        this.rigCatalog = rigCatalog;
        this.allocationRepository = allocationRepository;
        this.historyPort = historyPort;
        this.notificationService = notificationService;
    }

    public void ensureBalance(Long walletId, BigDecimal requiredAmount) {
        BigDecimal current = ledgerService.getBalance(walletId);
        if (current.compareTo(requiredAmount) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Insufficient wallet balance for the requested mining allocation.");
        }
    }

    public void debitRental(Long walletId, BigDecimal rentalCost, String rigCode) {
        ledgerService.updateBalance(walletId, normalize(rentalCost).negate(), "MINING_ALLOC:" + rigCode);
    }

    @Transactional
    public void settleIfDue(MiningAllocationEntity allocation) {
        if (!"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
            return;
        }
        if (allocation.getEndsAt() == null || allocation.getEndsAt().isAfter(LocalDateTime.now())) {
            return;
        }

        ledgerService.updateBalance(
                allocation.getWalletId(),
                normalize(allocation.getProjectedNetYieldBtc()),
                "MINING_SETTLEMENT:" + allocation.getId());

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
                "Locacao de hashpower concluida",
                "Os rendimentos projetados da locacao foram creditados na sua carteira.");
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
            ledgerService.updateBalance(allocation.getWalletId(), creditBack, "MINING_CANCEL:" + allocation.getId());
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
        notifyUser(userId, "Locacao de hashpower cancelada",
                "A locacao foi cancelada e o ajuste proporcional foi creditado.");

        return saved;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private void notifyUser(Long userId, String title, String body) {
        try {
            notificationService.notifyUser(userId, title, body);
        } catch (Exception ex) {
            log.warn("Failed to emit mining notification: {}", ex.getMessage());
        }
    }
}
