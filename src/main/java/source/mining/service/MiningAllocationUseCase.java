package source.mining.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.mining.dto.MiningAllocationRequestDTO;
import source.mining.dto.MiningAllocationResponseDTO;
import source.mining.entity.MiningAllocationEntity;
import source.mining.entity.MiningRigOfferEntity;
import source.mining.exception.MiningExceptions;
import source.mining.repository.MiningAllocationRepository;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.service.NotificationService;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MiningAllocationUseCase {

    private static final Logger log = LoggerFactory.getLogger(MiningAllocationUseCase.class);

    private final MiningAllocationRepository allocationRepository;
    private final WalletLookupPort walletLookupPort;
    private final TransactionalAuthenticationPort transactionalAuthenticationPort;
    private final RigCatalog rigCatalog;
    private final MiningSettlementService settlementService;
    private final MiningHistoryPort historyPort;
    private final NotificationService notificationService;

    public MiningAllocationUseCase(
            MiningAllocationRepository allocationRepository,
            WalletLookupPort walletLookupPort,
            TransactionalAuthenticationPort transactionalAuthenticationPort,
            RigCatalog rigCatalog,
            MiningSettlementService settlementService,
            MiningHistoryPort historyPort,
            NotificationService notificationService) {
        this.allocationRepository = allocationRepository;
        this.walletLookupPort = walletLookupPort;
        this.transactionalAuthenticationPort = transactionalAuthenticationPort;
        this.rigCatalog = rigCatalog;
        this.settlementService = settlementService;
        this.historyPort = historyPort;
        this.notificationService = notificationService;
    }

    @Transactional
    public MiningAllocationResponseDTO createAllocation(Long userId, MiningAllocationRequestDTO request) {
        WalletEntity wallet = resolveWallet(userId, request.walletName());
        transactionalAuthenticationPort.authorize(TransactionalAuthenticationRequest.walletOutbound(
                userId,
                wallet.getUser() != null ? wallet.getUser().getId() : null,
                wallet.getTotpSecret(),
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase()));

        MiningRigOfferEntity rig = rigCatalog.getActiveRig(request.rigId());
        validateDuration(request, rig);

        BigDecimal allocatedHashrate = resolveRequestedHashrate(request, rig);
        if (allocatedHashrate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MiningExceptions.InvalidMiningAllocation("Allocated hashrate must be positive.");
        }
        rigCatalog.ensureAvailableHashrate(rig, allocatedHashrate);

        BigDecimal rentalCost = calculateRentalCost(rig, allocatedHashrate, request.durationHours());
        settlementService.ensureBalance(wallet.getId(), rentalCost);

        BigDecimal projectedGrossYield = calculateGrossYield(rig, allocatedHashrate, request.durationHours());
        BigDecimal projectedNetYield = normalize(projectedGrossYield.multiply(rig.getProjectedYieldMultiplier()));

        settlementService.debitRental(wallet.getId(), rentalCost, rig.getRigCode());
        rigCatalog.reserveHashrate(rig, allocatedHashrate);

        LocalDateTime now = LocalDateTime.now();
        MiningAllocationEntity allocation = new MiningAllocationEntity();
        allocation.setUserId(userId);
        allocation.setWalletId(wallet.getId());
        allocation.setRigId(rig.getId());
        allocation.setWalletNameSnapshot(wallet.getName());
        allocation.setRigNameSnapshot(rig.getDisplayName());
        allocation.setAlgorithm(rig.getAlgorithm());
        allocation.setHashUnit(rig.getHashUnit());
        allocation.setAllocatedHashrate(allocatedHashrate);
        allocation.setDurationHours(request.durationHours());
        allocation.setRentalCostBtc(rentalCost);
        allocation.setProjectedGrossYieldBtc(projectedGrossYield);
        allocation.setProjectedNetYieldBtc(projectedNetYield);
        allocation.setPayoutAddress(firstNonBlank(request.payoutAddress(), wallet.getDepositAddress()));
        allocation.setPoolUrl(request.poolUrl());
        allocation.setWorkerName(request.workerName());
        allocation.setProviderRentalReference("rent_" + rig.getRigCode() + "_" + UUID.randomUUID().toString().substring(0, 8));
        allocation.setStatus("ACTIVE");
        allocation.setStartsAt(now);
        allocation.setEndsAt(now.plusHours(request.durationHours()));
        allocation = allocationRepository.save(allocation);

        historyPort.record(new MiningHistoryPort.MiningHistoryRecord(
                userId,
                wallet.getName(),
                rig.getDisplayName(),
                "MINING_HASHPOWER_ALLOCATION",
                rentalCost,
                "ACTIVE",
                allocation.getProviderRentalReference(),
                "Mining allocation opened for " + allocatedHashrate + " " + rig.getHashUnit(),
                LocalDateTime.now()));
        notifyUser(
                userId,
                NotificationKind.MINING_STARTED,
                NotificationSeverity.SUCCESS,
                "Locacao de hashpower iniciada",
                "Locacao do rig " + rig.getDisplayName() + " iniciada com sucesso.",
                "/mining",
                "mining_allocation",
                allocation.getId() != null ? allocation.getId().toString() : null,
                Map.of(
                        "walletName", wallet.getName(),
                        "rigName", rig.getDisplayName(),
                        "durationHours", String.valueOf(request.durationHours()),
                        "amountBtc", rentalCost.toPlainString()));

        return toAllocationDTO(allocation);
    }

    @Transactional
    public List<MiningAllocationResponseDTO> listAllocations(Long userId) {
        List<MiningAllocationEntity> allocations = allocationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        allocations.forEach(settlementService::settleIfDue);
        return allocations.stream()
                .map(this::toAllocationDTO)
                .toList();
    }

    @Transactional
    public MiningAllocationResponseDTO getAllocation(Long userId, UUID allocationId) {
        MiningAllocationEntity allocation = allocationRepository.findByIdAndUserId(allocationId, userId)
                .orElseThrow(() -> new MiningExceptions.MiningAllocationNotFound("Mining allocation not found."));
        settlementService.settleIfDue(allocation);
        return toAllocationDTO(allocation);
    }

    @Transactional
    public MiningAllocationResponseDTO cancelAllocation(Long userId, UUID allocationId) {
        MiningAllocationEntity allocation = allocationRepository.findByIdAndUserId(allocationId, userId)
                .orElseThrow(() -> new MiningExceptions.MiningAllocationNotFound("Mining allocation not found."));
        return toAllocationDTO(settlementService.cancel(userId, allocation));
    }

    private WalletEntity resolveWallet(Long userId, String walletName) {
        WalletEntity wallet = walletLookupPort.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new source.wallet.exceptions.WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }

    private void validateDuration(MiningAllocationRequestDTO request, MiningRigOfferEntity rig) {
        if (request.durationHours() == null || request.durationHours() <= 0) {
            throw new MiningExceptions.InvalidMiningAllocation("Rental duration must be greater than zero.");
        }
        if (request.durationHours() < rig.getMinRentalHours() || request.durationHours() > rig.getMaxRentalHours()) {
            throw new MiningExceptions.InvalidMiningAllocation(
                    "Rental duration must stay within the rig limits.");
        }
    }

    private BigDecimal resolveRequestedHashrate(MiningAllocationRequestDTO request, MiningRigOfferEntity rig) {
        if (request.requestedHashrate() != null && request.requestedHashrate().compareTo(BigDecimal.ZERO) > 0) {
            return normalize(request.requestedHashrate());
        }
        if (request.budgetBtc() != null && request.budgetBtc().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal denominator = rig.getPricePerUnitDayBtc()
                    .multiply(new BigDecimal(request.durationHours()))
                    .divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP);
            if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
                throw new MiningExceptions.InvalidMiningAllocation("Unable to calculate hashrate for the requested budget.");
            }
            return normalize(request.budgetBtc().divide(denominator, 8, RoundingMode.DOWN));
        }
        throw new MiningExceptions.InvalidMiningAllocation(
                "Either requestedHashrate or budgetBtc must be provided.");
    }

    private BigDecimal calculateRentalCost(MiningRigOfferEntity rig, BigDecimal allocatedHashrate, Integer durationHours) {
        return normalize(
                rig.getPricePerUnitDayBtc()
                        .multiply(allocatedHashrate)
                        .multiply(new BigDecimal(durationHours))
                        .divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP));
    }

    private BigDecimal calculateGrossYield(MiningRigOfferEntity rig, BigDecimal allocatedHashrate, Integer durationHours) {
        return normalize(
                rig.getProjectedBtcYieldPerUnitDay()
                        .multiply(allocatedHashrate)
                        .multiply(new BigDecimal(durationHours))
                        .divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP));
    }

    private MiningAllocationResponseDTO toAllocationDTO(MiningAllocationEntity entity) {
        return new MiningAllocationResponseDTO(
                entity.getId(),
                entity.getRigId(),
                entity.getRigNameSnapshot(),
                entity.getWalletNameSnapshot(),
                entity.getAlgorithm(),
                entity.getAllocatedHashrate(),
                entity.getHashUnit(),
                entity.getDurationHours(),
                entity.getRentalCostBtc(),
                entity.getProjectedGrossYieldBtc(),
                entity.getProjectedNetYieldBtc(),
                entity.getRefundedAmountBtc(),
                entity.getStatus(),
                entity.getProviderRentalReference(),
                entity.getPayoutAddress(),
                entity.getPoolUrl(),
                entity.getWorkerName(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getSettledAt());
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void notifyUser(
            Long userId,
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String body,
            String deeplink,
            String entityType,
            String entityId,
            Map<String, String> metadata) {
        try {
            notificationService.notifyUser(
                    userId,
                    kind,
                    severity,
                    title,
                    body,
                    deeplink,
                    entityType,
                    entityId,
                    metadata);
        } catch (Exception ex) {
            log.warn("Failed to emit mining notification: {}", ex.getMessage());
        }
    }
}
