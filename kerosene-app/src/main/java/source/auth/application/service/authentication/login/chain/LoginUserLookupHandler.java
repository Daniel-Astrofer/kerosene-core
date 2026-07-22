package source.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.login.LoginCredentialRules;
import source.auth.application.service.authentication.login.LoginValidationContext;

@Component
@Order(30)
public class LoginUserLookupHandler extends AbstractLoginValidationHandler {

    private final LoginCredentialRules rules;

    public LoginUserLookupHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(LoginValidationContext context) {
        context.setUser(rules.loadUser(context.getNormalizedUsername()));
        handleNext(context);
    }
}
