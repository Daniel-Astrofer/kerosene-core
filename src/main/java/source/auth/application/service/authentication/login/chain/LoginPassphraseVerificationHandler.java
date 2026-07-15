package source.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.login.LoginCredentialRules;
import source.auth.application.service.authentication.login.LoginValidationContext;

@Component
@Order(40)
public class LoginPassphraseVerificationHandler extends AbstractLoginValidationHandler {

    private final LoginCredentialRules rules;

    public LoginPassphraseVerificationHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(LoginValidationContext context) {
        char[] normalizedPassphrase = rules.normalizePassphrase(context.getDto().getPassphrase());
        context.setNormalizedPassphrase(normalizedPassphrase);
        rules.verifyPassphrase(normalizedPassphrase, context.getUser());
        handleNext(context);
    }
}
