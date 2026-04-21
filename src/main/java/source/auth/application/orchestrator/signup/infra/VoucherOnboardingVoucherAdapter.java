package source.auth.application.orchestrator.signup.infra;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import source.auth.application.orchestrator.signup.port.OnboardingVoucherPort;
import source.voucher.service.VoucherService;

@Component
public class VoucherOnboardingVoucherAdapter implements OnboardingVoucherPort {

    private final VoucherService voucherService;

    public VoucherOnboardingVoucherAdapter(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Override
    public void createAndClaim(Long userId, String txid, BigDecimal amountPaid) {
        voucherService.createAndClaimOnboardingVoucher(userId, txid, amountPaid);
    }
}
