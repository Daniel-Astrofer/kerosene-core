package source.auth.application.service.security.profile;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.AuthExceptions;
import source.auth.model.enums.AccountSecurityType;

@Component
@Order(20)
public class MultisigAccountSecurityProfileHandler extends AbstractAccountSecurityProfileHandler {

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
