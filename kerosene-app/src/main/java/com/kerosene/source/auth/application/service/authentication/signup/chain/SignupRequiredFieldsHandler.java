package source.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.signup.SignupCredentialRules;
import source.auth.application.service.authentication.signup.SignupValidationContext;

@Component
@Order(10)
public class SignupRequiredFieldsHandler extends AbstractSignupValidationHandler {

    private final SignupCredentialRules rules;

    public SignupRequiredFieldsHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(SignupValidationContext context) {
        rules.checkUsernameNotNull(context.username());
        rules.checkPassphraseNotNull(context.passphrase());
        handleNext(context);
    }
}
