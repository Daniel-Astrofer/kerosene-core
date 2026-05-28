package source.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.repository.LedgerTransactionHistoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WalletCardProfileService {

    private static final Map<String, Set<String>> MOVEMENT_RULES = Map.of(
            "INTERNAL", Set.of("CONCLUDED"),
            "DEPOSIT", Set.of("CONCLUDED"),
            "EXTERNAL_DEPOSIT", Set.of("CONCLUDED"),
            "MINING_PAYOUT_SETTLEMENT", Set.of("COMPLETED"),
            "EXTERNAL_ONCHAIN_WITHDRAWAL", Set.of("PENDING", "CONFIRMED", "CONCLUDED", "COMPLETED", "SETTLED"),
            "EXTERNAL_LIGHTNING_PAYMENT", Set.of("PENDING", "CONFIRMED", "CONCLUDED", "COMPLETED", "SETTLED"));

    private final UserServiceContract userService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final int minimumAccountAgeMonths;
    private final BigDecimal whiteMovementThreshold;
    private final BigDecimal blackMovementThreshold;
    private final BigDecimal bronzeFeeRate;
    private final BigDecimal whiteFeeRate;
    private final BigDecimal blackFeeRate;

    public WalletCardProfileService(
            UserServiceContract userService,
            LedgerTransactionHistoryRepository historyRepository,
            @Value("${wallet.card.account-age-months-threshold:6}") int minimumAccountAgeMonths,
            @Value("${wallet.card.white.monthly-movement-threshold:1500}") BigDecimal whiteMovementThreshold,
            @Value("${wallet.card.black.monthly-movement-threshold:3000}") BigDecimal blackMovementThreshold,
            @Value("${wallet.card.bronze.fee-rate:0.009}") BigDecimal bronzeFeeRate,
            @Value("${wallet.card.white.fee-rate:0.008}") BigDecimal whiteFeeRate,
            @Value("${wallet.card.black.fee-rate:0.007}") BigDecimal blackFeeRate) {
        this.userService = userService;
        this.historyRepository = historyRepository;
        this.minimumAccountAgeMonths = minimumAccountAgeMonths;
        this.whiteMovementThreshold = normalizeAmount(whiteMovementThreshold);
        this.blackMovementThreshold = normalizeAmount(blackMovementThreshold);
        this.bronzeFeeRate = normalizeRate(bronzeFeeRate);
        this.whiteFeeRate = normalizeRate(whiteFeeRate);
        this.blackFeeRate = normalizeRate(blackFeeRate);
    }

    public WalletCardProfile resolveProfile(Long userId) {
        UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));

        BigDecimal monthlyMovement = calculateMonthlyMovement(userId);
        boolean eligibleByAge = hasMinimumAccountAge(user.getCreatedAt());

        WalletCardType cardType = WalletCardType.BRONZE;
        BigDecimal applicableRate = bronzeFeeRate;

        if (eligibleByAge && monthlyMovement.compareTo(blackMovementThreshold) > 0) {
            cardType = WalletCardType.BLACK;
            applicableRate = blackFeeRate;
        } else if (eligibleByAge && monthlyMovement.compareTo(whiteMovementThreshold) > 0) {
            cardType = WalletCardType.WHITE;
            applicableRate = whiteFeeRate;
        }

        return new WalletCardProfile(cardType, applicableRate, applicableRate, monthlyMovement);
    }

    public BigDecimal calculateWithdrawalFee(Long userId, BigDecimal amount) {
        return resolveProfile(userId).calculateWithdrawalFee(amount);
    }

    public BigDecimal calculateDepositFee(Long userId, BigDecimal amount) {
        return resolveProfile(userId).calculateDepositFee(amount);
    }

    private BigDecimal calculateMonthlyMovement(Long userId) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);

        return historyRepository.findMovementHistoryForUser(userId, start, end).stream()
                .filter(this::countsTowardsMonthlyMovement)
                .map(LedgerTransactionHistory::getAmount)
                .filter(amount -> amount != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);
    }

    private boolean hasMinimumAccountAge(LocalDateTime createdAt) {
        return createdAt != null
                && !createdAt.isAfter(LocalDateTime.now().minusMonths(minimumAccountAgeMonths));
    }

    private boolean countsTowardsMonthlyMovement(LedgerTransactionHistory history) {
        if (history == null || history.getTransactionType() == null || history.getStatus() == null) {
            return false;
        }

        String type = history.getTransactionType().trim().toUpperCase(Locale.ROOT);
        String status = history.getStatus().trim().toUpperCase(Locale.ROOT);

        return MOVEMENT_RULES.getOrDefault(type, Set.of()).contains(status);
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
