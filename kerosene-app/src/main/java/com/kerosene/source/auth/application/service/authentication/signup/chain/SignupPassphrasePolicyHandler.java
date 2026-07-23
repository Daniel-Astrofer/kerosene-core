package source.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.signup.SignupCredentialRules;
import source.auth.application.service.authentication.signup.SignupValidationContext;

@Component
@Order(30)
public class SignupPassphrasePolicyHandler extends AbstractSignupValidationHandler {

    private final SignupCredentialRules rules;

    public SignupPassphrasePolicyHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(SignupValidationContext context) {
        rules.checkPassphraseLength(context.passphrase());
        rules.checkPassphraseBip39(context.passphrase());
        handleNext(context);
    }
}
