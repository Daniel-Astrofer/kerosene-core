package source.auth.application.service.security.profile;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.model.enums.AccountSecurityType;

@Component
@Order(30)
public class DefaultAccountSecurityProfileHandler extends AbstractAccountSecurityProfileHandler {

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
