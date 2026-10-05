package com.kerosene.auth.application.orchestrator.signup.port;

import java.math.BigDecimal;

/** Port for creating and claiming any voucher associated with completed onboarding payment. */
public interface OnboardingVoucherPort {

    /** Creates the onboarding voucher and records its claim for the account. */
    /** @param userId persisted account identifier */
    /** @param txid payment transaction identifier */
    /** @param amountPaid confirmed payment amount */
    void createAndClaim(Long userId, String txid, BigDecimal amountPaid);
}
