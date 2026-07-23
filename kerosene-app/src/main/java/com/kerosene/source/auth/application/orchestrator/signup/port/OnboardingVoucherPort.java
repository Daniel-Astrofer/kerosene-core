package source.auth.application.orchestrator.signup.port;

import java.math.BigDecimal;

public interface OnboardingVoucherPort {

    void createAndClaim(Long userId, String txid, BigDecimal amountPaid);
}
