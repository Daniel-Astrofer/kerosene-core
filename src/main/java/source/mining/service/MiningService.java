package source.mining.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerService;
import source.mining.dto.MiningAllocationRequestDTO;
import source.mining.dto.MiningAllocationResponseDTO;
import source.mining.dto.MiningRigOfferDTO;
import source.mining.entity.MiningAllocationEntity;
import source.mining.entity.MiningRigOfferEntity;
import source.mining.exception.MiningExceptions;
import source.mining.repository.MiningAllocationRepository;
import source.mining.repository.MiningRigOfferRepository;
import source.transactions.service.WalletAuthorizationService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MiningService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MiningService.class);

    private final MiningRigOfferRepository rigOfferRepository;
    private final MiningAllocationRepository allocationRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final WalletAuthorizationService walletAuthorizationService;
    private final source.notification.service.NotificationService notificationService;

    public MiningService(
            MiningRigOfferRepository rigOfferRepository,
            MiningAllocationRepository allocationRepository,
            WalletService walletService,
            LedgerService ledgerService,
            LedgerTransactionHistoryRepository historyRepository,
            WalletAuthorizationService walletAuthorizationService,
            source.notification.service.NotificationService notificationService) {
        this.rigOfferRepository = rigOfferRepository;
        this.allocationRepository = allocationRepository;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.historyRepository = historyRepository;
        this.walletAuthorizationService = walletAuthorizationService;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<MiningRigOfferDTO> listRigOffers() {
        ensureDefaultOffers();
        return rigOfferRepository.findByActiveTrueOrderByAlgorithmAscDisplayNameAsc().stream()
                .map(this::toRigOfferDTO)
                .toList();
    }

    @Transactional
    public MiningAllocationResponseDTO createAllocation(Long userId, MiningAllocationRequestDTO request) {
        ensureDefaultOffers();
        WalletEntity wallet = resolveWallet(userId, request.walletName());
        walletAuthorizationService.authorizeOutboundTransfer(
                userId,
                wallet,
                request.totpCode(),
                request.passkeyAssertionResponseJSON(),
                request.confirmationPassphrase());

        MiningRigOfferEntity rig = rigOfferRepository.findByIdAndActiveTrue(request.rigId())
                .orElseThrow(() -> new MiningExceptions.RigNotFound("The selected mining rig is not available."));

        if (request.durationHours() == null || request.durationHours() <= 0) {
            throw new MiningExceptions.InvalidMiningAllocation("Rental duration must be greater than zero.");
        }
        if (request.durationHours() < rig.getMinRentalHours() || request.durationHours() > rig.getMaxRentalHours()) {
            throw new MiningExceptions.InvalidMiningAllocation(
                    "Rental duration must stay within the rig limits.");
        }

        BigDecimal allocatedHashrate = resolveRequestedHashrate(request, rig);
        if (allocatedHashrate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MiningExceptions.InvalidMiningAllocation("Allocated hashrate must be positive.");
        }
        if (allocatedHashrate.compareTo(rig.getAvailableHashrate()) > 0) {
            throw new MiningExceptions.InvalidMiningAllocation("Requested hashrate exceeds current available capacity.");
        }

        BigDecimal rentalCost = normalize(
                rig.getPricePerUnitDayBtc()
                        .multiply(allocatedHashrate)
                        .multiply(new BigDecimal(request.durationHours()))
                        .divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP));
        ensureBalance(wallet.getId(), rentalCost);

        BigDecimal projectedGrossYield = normalize(
                rig.getProjectedBtcYieldPerUnitDay()
                        .multiply(allocatedHashrate)
                        .multiply(new BigDecimal(request.durationHours()))
                        .divide(new BigDecimal("24"), 8, RoundingMode.HALF_UP));
        BigDecimal projectedNetYield = normalize(projectedGrossYield.multiply(rig.getProjectedYieldMultiplier()));

        ledgerService.updateBalance(wallet.getId(), rentalCost.negate(), "MINING_ALLOC:" + rig.getRigCode());

        rig.setAvailableHashrate(normalize(rig.getAvailableHashrate().subtract(allocatedHashrate)));
        rigOfferRepository.save(rig);

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
        allocation.setStartsAt(LocalDateTime.now());
        allocation.setEndsAt(LocalDateTime.now().plusHours(request.durationHours()));
        allocation = allocationRepository.save(allocation);

        recordHistory(
                userId,
                wallet.getName(),
                rig.getDisplayName(),
                "MINING_HASHPOWER_ALLOCATION",
                rentalCost,
                "ACTIVE",
                allocation.getProviderRentalReference(),
                "Mining allocation opened for " + allocatedHashrate + " " + rig.getHashUnit());
        notifyUser(userId, "Locacao de hashpower iniciada",
                "Locacao do rig " + rig.getDisplayName() + " iniciada com sucesso.");

        return toAllocationDTO(allocation);
    }

    @Transactional
    public List<MiningAllocationResponseDTO> listAllocations(Long userId) {
        ensureDefaultOffers();
        List<MiningAllocationEntity> allocations = allocationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        allocations.forEach(this::settleIfDue);
        return allocations.stream()
                .map(this::toAllocationDTO)
                .toList();
    }

    @Transactional
    public MiningAllocationResponseDTO getAllocation(Long userId, UUID allocationId) {
        MiningAllocationEntity allocation = allocationRepository.findByIdAndUserId(allocationId, userId)
                .orElseThrow(() -> new MiningExceptions.MiningAllocationNotFound("Mining allocation not found."));
        settleIfDue(allocation);
        return toAllocationDTO(allocation);
    }

    @Transactional
    public MiningAllocationResponseDTO cancelAllocation(Long userId, UUID allocationId) {
        MiningAllocationEntity allocation = allocationRepository.findByIdAndUserId(allocationId, userId)
                .orElseThrow(() -> new MiningExceptions.MiningAllocationNotFound("Mining allocation not found."));
        if (!"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
            throw new MiningExceptions.MiningAllocationStateException(
                    "Only active allocations can be cancelled.");
        }

        MiningRigOfferEntity rig = rigOfferRepository.findById(allocation.getRigId())
                .orElseThrow(() -> new MiningExceptions.RigNotFound("The underlying rig was not found."));

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

        rig.setAvailableHashrate(normalize(rig.getAvailableHashrate().add(allocation.getAllocatedHashrate())));
        rigOfferRepository.save(rig);

        allocation.setRefundedAmountBtc(refundable);
        allocation.setSettledAt(now);
        allocation.setStatus("CANCELLED");
        allocationRepository.save(allocation);

        recordHistory(
                userId,
                allocation.getWalletNameSnapshot(),
                allocation.getRigNameSnapshot(),
                "MINING_HASHPOWER_CANCEL",
                creditBack,
                "CANCELLED",
                allocation.getProviderRentalReference(),
                "Mining allocation cancelled with pro-rated refund.");
        notifyUser(userId, "Locacao de hashpower cancelada",
                "A locacao foi cancelada e o ajuste proporcional foi creditado.");

        return toAllocationDTO(allocation);
    }

    private WalletEntity resolveWallet(Long userId, String walletName) {
        WalletEntity wallet = walletService.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new source.wallet.exceptions.WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }

    private void ensureDefaultOffers() {
        if (rigOfferRepository.count() > 0) {
            return;
        }

        rigOfferRepository.save(createDefaultOffer(
                "sha256-hydro-240",
                "Hydro SHA256 240TH",
                "SHA256",
                "TH",
                new BigDecimal("0.00000850"),
                new BigDecimal("0.00000720"),
                new BigDecimal("0.98500000"),
                new BigDecimal("1200.00000000"),
                1,
                168));
        rigOfferRepository.save(createDefaultOffer(
                "sha256-pro-150",
                "Pro SHA256 150TH",
                "SHA256",
                "TH",
                new BigDecimal("0.00000720"),
                new BigDecimal("0.00000610"),
                new BigDecimal("0.98250000"),
                new BigDecimal("900.00000000"),
                1,
                168));
        rigOfferRepository.save(createDefaultOffer(
                "scrypt-rack-18g",
                "Scrypt Rack 18GH",
                "SCRYPT",
                "GH",
                new BigDecimal("0.00012000"),
                new BigDecimal("0.00010100"),
                new BigDecimal("0.98000000"),
                new BigDecimal("180.00000000"),
                1,
                72));
    }

    private MiningRigOfferEntity createDefaultOffer(
            String rigCode,
            String displayName,
            String algorithm,
            String hashUnit,
            BigDecimal pricePerUnitDayBtc,
            BigDecimal projectedBtcYieldPerUnitDay,
            BigDecimal projectedYieldMultiplier,
            BigDecimal availableHashrate,
            int minHours,
            int maxHours) {
        MiningRigOfferEntity rig = new MiningRigOfferEntity();
        rig.setRigCode(rigCode);
        rig.setDisplayName(displayName);
        rig.setAlgorithm(algorithm);
        rig.setHashUnit(hashUnit);
        rig.setPricePerUnitDayBtc(pricePerUnitDayBtc);
        rig.setProjectedBtcYieldPerUnitDay(projectedBtcYieldPerUnitDay);
        rig.setProjectedYieldMultiplier(projectedYieldMultiplier);
        rig.setAvailableHashrate(availableHashrate);
        rig.setMinRentalHours(minHours);
        rig.setMaxRentalHours(maxHours);
        rig.setProvider("KEROSENE_INTERNAL");
        rig.setActive(true);
        return rig;
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

    private void ensureBalance(Long walletId, BigDecimal requiredAmount) {
        BigDecimal current = ledgerService.getBalance(walletId);
        if (current.compareTo(requiredAmount) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Insufficient wallet balance for the requested mining allocation.");
        }
    }

    private void settleIfDue(MiningAllocationEntity allocation) {
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

        recordHistory(
                allocation.getUserId(),
                allocation.getRigNameSnapshot(),
                allocation.getWalletNameSnapshot(),
                "MINING_PAYOUT_SETTLEMENT",
                allocation.getProjectedNetYieldBtc(),
                "COMPLETED",
                allocation.getProviderRentalReference(),
                "Mining allocation settled and proceeds credited.");
        notifyUser(
                allocation.getUserId(),
                "Locacao de hashpower concluida",
                "Os rendimentos projetados da locacao foram creditados na sua carteira.");
    }

    private void recordHistory(
            Long userId,
            String senderIdentifier,
            String receiverIdentifier,
            String transactionType,
            BigDecimal amount,
            String status,
            String blockchainTxid,
            String context) {
        LedgerTransactionHistory history = new LedgerTransactionHistory();
        history.setId(UUID.randomUUID());
        history.setSenderUserId(userId);
        history.setReceiverUserId(userId);
        history.setSenderIdentifier(senderIdentifier);
        history.setReceiverIdentifier(receiverIdentifier);
        history.setTransactionType(transactionType);
        history.setAmount(normalize(amount));
        history.setStatus(status);
        history.setBlockchainTxid(blockchainTxid);
        history.setContext(context);
        history.setCreatedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    private MiningRigOfferDTO toRigOfferDTO(MiningRigOfferEntity entity) {
        return new MiningRigOfferDTO(
                entity.getId(),
                entity.getRigCode(),
                entity.getDisplayName(),
                entity.getAlgorithm(),
                entity.getHashUnit(),
                entity.getAvailableHashrate(),
                entity.getPricePerUnitDayBtc(),
                entity.getProjectedBtcYieldPerUnitDay(),
                entity.getMinRentalHours(),
                entity.getMaxRentalHours(),
                entity.getProvider());
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

    private void notifyUser(Long userId, String title, String body) {
        try {
            notificationService.notifyUser(userId, title, body);
        } catch (Exception ex) {
            log.warn("Failed to emit mining notification: {}", ex.getMessage());
        }
    }
}
