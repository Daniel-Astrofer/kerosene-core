package source.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.login.LoginCredentialRules;
import source.auth.application.service.authentication.login.LoginValidationContext;

@Component
@Order(20)
public class LoginRateLimitHandler extends AbstractLoginValidationHandler {

    private final LoginCredentialRules rules;

    public LoginRateLimitHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(LoginValidationContext context) {
        context.setRateLimitKey(rules.registerRateLimitAttempt(context.getNormalizedUsername()));
        handleNext(context);
    }
}
