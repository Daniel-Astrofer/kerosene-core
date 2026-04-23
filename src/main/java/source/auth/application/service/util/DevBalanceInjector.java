package source.auth.application.service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.model.entity.UserDataBase;
import source.ledger.service.LedgerService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.List;

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

    private final WalletService walletService;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final boolean enabled;

    public DevBalanceInjector(WalletService walletService,
                               LedgerService ledgerService,
                               UserRepository userRepository,
                               @Value("${app.dev.inject-test-balance:false}") boolean enabled) {
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
        this.enabled = enabled;
    }

    /**
     * Injects 100 BTC if the user hasn't claimed it yet.
     * Disabled by default and must be explicitly enabled via configuration.
     */
    public void injectTestBalance(UserDataBase user) {
        claimTestBalance(user);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ClaimOutcome claimTestBalance(UserDataBase user) {
        if (!enabled) {
            return ClaimOutcome.DISABLED;
        }

        if (Boolean.TRUE.equals(user.getTestBalanceClaimed())) {
            return ClaimOutcome.ALREADY_CLAIMED;
        }

        try {
            List<WalletEntity> wallets = walletService.findByUserId(user.getId());
            if (wallets.isEmpty()) {
                log.warn("[DEV] User {} has no wallets to inject balance.", user.getUsername());
                return ClaimOutcome.NO_WALLET;
            }

            // Grant to the first wallet
            WalletEntity wallet = wallets.get(0);
            BigDecimal amount = new BigDecimal("100.00000000");

            ledgerService.updateBalance(wallet.getId(), amount, "DEV_INITIAL_GRANT");

            user.setTestBalanceClaimed(true);
            userRepository.save(user);

            log.info("[DEV] Successfully injected 100 BTC one-time bonus for user {}", user.getUsername());
            return ClaimOutcome.CLAIMED;
        } catch (Exception e) {
            log.error("[DEV] Failed to inject test balance for user {}", user.getUsername(), e);
            return ClaimOutcome.ERROR;
        }
    }
}
