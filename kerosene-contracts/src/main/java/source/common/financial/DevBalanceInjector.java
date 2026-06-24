package source.common.financial;

import java.util.UUID;

public interface DevBalanceInjector {

    enum ClaimOutcome {
        CLAIMED,
        DISABLED,
        ALREADY_CLAIMED,
        NO_WALLET,
        ERROR
    }

    boolean isEnabled();

    default void injectTestBalance(Long userId) {
        claimTestBalance(userId, null);
    }

    default ClaimOutcome claimTestBalance(Long userId) {
        return claimTestBalance(userId, null);
    }

    ClaimOutcome claimTestBalance(Long userId, UUID preferredWalletId);
}
