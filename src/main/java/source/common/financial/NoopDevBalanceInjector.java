package source.common.financial;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnMissingBean(DevBalanceInjector.class)
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
