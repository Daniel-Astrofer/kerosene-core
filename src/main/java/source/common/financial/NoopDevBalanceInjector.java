package source.common.financial;

import java.util.UUID;

public class NoopDevBalanceInjector implements DevBalanceInjector {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public ClaimOutcome claimTestBalance(Long userId, UUID preferredWalletId) {
        return ClaimOutcome.DISABLED;
    }
}
