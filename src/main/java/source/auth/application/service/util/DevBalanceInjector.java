package source.auth.application.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.model.entity.UserDataBase;
import source.kfe.model.KfeWalletEntity;
import source.kfe.repository.KfeWalletRepository;
import source.kfe.service.KfeBalanceService;
import source.notification.service.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
public class DevBalanceInjector {

    private static final Logger log = LoggerFactory.getLogger(DevBalanceInjector.class);

    public enum ClaimOutcome {
        CLAIMED,
        DISABLED,
        ALREADY_CLAIMED,
        NO_WALLET,
        ERROR
    }

    private final KfeWalletRepository walletRepository;
    private final KfeBalanceService balanceService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final boolean enabled;

    public DevBalanceInjector(KfeWalletRepository walletRepository,
                               KfeBalanceService balanceService,
                               UserRepository userRepository,
                               NotificationService notificationService,
                               @Value("${app.dev.inject-test-balance:false}") boolean enabled) {
        this.walletRepository = walletRepository;
        this.balanceService = balanceService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.enabled = enabled;
    }

    /**
     * Injects 100 BTC if the user hasn't claimed it yet.
     * Disabled by default and must be explicitly enabled via configuration.
     */
    public void injectTestBalance(UserDataBase user) {
        claimTestBalance(user, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ClaimOutcome claimTestBalance(UserDataBase user) {
        return claimTestBalance(user, null);
    }

    public ClaimOutcome claimTestBalance(Long userId, UUID preferredWalletId) {
        return userRepository.findById(userId)
                .map(user -> claimTestBalance(user, preferredWalletId))
                .orElse(ClaimOutcome.NO_WALLET);
    }

    public ClaimOutcome claimTestBalance(UserDataBase user, UUID preferredWalletId) {
        if (!enabled) {
            return ClaimOutcome.DISABLED;
        }

        if (Boolean.TRUE.equals(user.getTestBalanceClaimed())) {
            return ClaimOutcome.ALREADY_CLAIMED;
        }

        try {
            KfeWalletEntity wallet = preferredWalletId != null
                    ? walletRepository.findByIdAndUserId(preferredWalletId, user.getId()).orElse(null)
                    : walletRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                            .stream()
                            .findFirst()
                            .orElse(null);
            if (wallet == null) {
                log.warn("[DEV] User {} has no wallets to inject balance.", user.getUsername());
                return ClaimOutcome.NO_WALLET;
            }

            BigDecimal amount = new BigDecimal("100.00000000");
            long sats = amount.movePointRight(8).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            balanceService.creditAvailable(wallet.getId(), "BTC", sats);

            user.setTestBalanceClaimed(true);
            userRepository.save(user);

            log.info("[DEV] Successfully injected 100 BTC one-time bonus for user {}", user.getUsername());

            // Emit a persistent notification for the mock balance
            notificationService.notifyUser(
                    user.getId(),
                    source.notification.model.NotificationKind.TRANSFER_RECEIVED,
                    source.notification.model.NotificationSeverity.SUCCESS,
                    "Saldo de Teste Creditado",
                    "Você recebeu " + amount.toPlainString() + " BTC de saldo mockado na carteira \"" + wallet.getLabel() + "\".",
                    "/home",
                    "wallet",
                    wallet.getId().toString(),
                    Map.of(
                            "walletId", wallet.getId().toString(),
                            "walletName", wallet.getLabel(),
                            "amountBtc", amount.toPlainString())
            );

            return ClaimOutcome.CLAIMED;
        } catch (Exception e) {
            log.error("[DEV] Failed to inject test balance for user {}", user.getUsername(), e);
            return ClaimOutcome.ERROR;
        }
    }
}
