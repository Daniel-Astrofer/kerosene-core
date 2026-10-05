package com.kerosene.auth.application.orchestrator.signup.infra;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.orchestrator.signup.port.OnboardingVoucherPort;
import com.kerosene.common.infra.logging.LogSanitizer;

/** Adapter for the optional signup voucher flow; this deployment currently logs and skips it. */
@Component
public class VoucherOnboardingVoucherAdapter implements OnboardingVoucherPort {

    /** Logger used for the explicit disabled-flow diagnostic. */
    private static final Logger log = LoggerFactory.getLogger(VoucherOnboardingVoucherAdapter.class);

    /**
     * Records a sanitized diagnostic and performs no voucher mutation while the flow is disabled.
     *
     * @param userId persisted account identifier
     * @param txid payment transaction identifier, logged only as a fingerprint
     * @param amountPaid confirmed amount (unused while this adapter is disabled)
     */
    @Override
    public void createAndClaim(Long userId, String txid, BigDecimal amountPaid) {
        log.info("[VoucherOnboardingVoucherAdapter] Voucher flow is disabled. Skipping onboarding voucher for userId={} txRef={}",
                userId, LogSanitizer.fingerprint(txid));
    }
}
