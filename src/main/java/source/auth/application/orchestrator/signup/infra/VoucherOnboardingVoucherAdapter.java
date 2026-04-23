package source.auth.application.orchestrator.signup.infra;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import source.auth.application.orchestrator.signup.port.OnboardingVoucherPort;

@Component
public class VoucherOnboardingVoucherAdapter implements OnboardingVoucherPort {

    private static final Logger log = LoggerFactory.getLogger(VoucherOnboardingVoucherAdapter.class);

    @Override
    public void createAndClaim(Long userId, String txid, BigDecimal amountPaid) {
        log.info("[VoucherOnboardingVoucherAdapter] Voucher flow is disabled. Skipping onboarding voucher for user {} txid={}",
                userId, txid);
    }
}
