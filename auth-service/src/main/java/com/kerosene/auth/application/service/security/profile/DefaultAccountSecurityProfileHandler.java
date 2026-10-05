package com.kerosene.auth.application.service.security.profile;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.model.enums.AccountSecurityType;

/** Final chain stage that clears fields irrelevant to standard account security. */
@Component
@Order(30)
public class DefaultAccountSecurityProfileHandler extends AbstractAccountSecurityProfileHandler {

    /** Applies safe defaults while preserving the threshold fields owned by advanced modes. */
    /** @param context current account profile */
    @Override
    public void handle(AccountSecurityProfileContext context) {
        if (context.getSecurityType() == AccountSecurityType.SHAMIR
                || context.getSecurityType() == AccountSecurityType.MULTISIG_2FA) {
            return;
        }

        context.getUser().setShamirTotalShares(null);
        context.getUser().setShamirThreshold(null);
        context.getUser().setMultisigThreshold(2);
    }
}
