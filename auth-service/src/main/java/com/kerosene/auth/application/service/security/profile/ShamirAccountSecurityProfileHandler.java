package com.kerosene.auth.application.service.security.profile;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.model.enums.AccountSecurityType;

/** Validates Shamir share and reconstruction threshold settings. */
@Component
@Order(10)
public class ShamirAccountSecurityProfileHandler extends AbstractAccountSecurityProfileHandler {

    /** Requires 2..8 shares and a threshold from 2 through the share count, then clears multisig data. */
    /** @param context current account profile */
    /** @throws AuthExceptions.InvalidCredentials when the Shamir configuration is incomplete or invalid */
    @Override
    public void handle(AccountSecurityProfileContext context) {
        if (context.getSecurityType() != AccountSecurityType.SHAMIR) {
            handleNext(context);
            return;
        }

        Integer totalShares = context.getUser().getShamirTotalShares();
        Integer threshold = context.getUser().getShamirThreshold();

        if (totalShares == null || threshold == null) {
            throw new AuthExceptions.InvalidCredentials(
                    "Shamir security requires total shares and threshold configuration.");
        }
        if (totalShares < 2 || totalShares > 8) {
            throw new AuthExceptions.InvalidCredentials("Shamir total shares must stay between 2 and 8.");
        }
        if (threshold < 2 || threshold > totalShares) {
            throw new AuthExceptions.InvalidCredentials(
                    "Shamir threshold must be between 2 and the configured total shares.");
        }

        context.getUser().setMultisigThreshold(null);
    }
}
