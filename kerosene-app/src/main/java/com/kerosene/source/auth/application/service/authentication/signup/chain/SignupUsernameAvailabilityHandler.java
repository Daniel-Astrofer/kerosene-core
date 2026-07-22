package source.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.signup.SignupCredentialRules;
import source.auth.application.service.authentication.signup.SignupValidationContext;

@Component
@Order(40)
public class SignupUsernameAvailabilityHandler extends AbstractSignupValidationHandler {

    private final SignupCredentialRules rules;

    public SignupUsernameAvailabilityHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(SignupValidationContext context) {
        rules.checkUsernameExists(context.username());
        handleNext(context);
    }
}
