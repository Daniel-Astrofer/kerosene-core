package com.kerosene.auth.application.service.security.profile;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.model.enums.AccountSecurityType;

/** Validates multisig threshold settings and clears Shamir-only fields for that mode. */
@Component
@Order(20)
public class MultisigAccountSecurityProfileHandler extends AbstractAccountSecurityProfileHandler {

    /** Validates a 2-of-2/3 multisig threshold or delegates when the user has another mode. */
    /** @param context current account profile */
    /** @throws AuthExceptions.InvalidCredentials when the threshold is outside 2..3 */
    @Override
    public void handle(AccountSecurityProfileContext context) {
        if (context.getSecurityType() != AccountSecurityType.MULTISIG_2FA) {
            handleNext(context);
            return;
        }

        Integer threshold = context.getUser().getMultisigThreshold();
        if (threshold == null) {
            threshold = 2;
            context.getUser().setMultisigThreshold(2);
        }
        if (threshold < 2 || threshold > 3) {
            throw new AuthExceptions.InvalidCredentials("Multisig vault threshold must be 2 or 3 factors.");
        }

        context.getUser().setShamirTotalShares(null);
        context.getUser().setShamirThreshold(null);
    }
}
