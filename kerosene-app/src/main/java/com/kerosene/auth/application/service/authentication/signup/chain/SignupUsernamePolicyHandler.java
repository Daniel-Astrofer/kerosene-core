package source.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.signup.SignupCredentialRules;
import source.auth.application.service.authentication.signup.SignupValidationContext;

@Component
@Order(20)
public class SignupUsernamePolicyHandler extends AbstractSignupValidationHandler {

    private final SignupCredentialRules rules;

    public SignupUsernamePolicyHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(SignupValidationContext context) {
        rules.checkUsernameFormat(context.username());
        rules.checkUsernameLength(context.username());
        handleNext(context);
    }
}
