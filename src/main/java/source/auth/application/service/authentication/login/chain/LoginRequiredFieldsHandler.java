package source.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import source.auth.application.service.authentication.login.LoginCredentialRules;
import source.auth.application.service.authentication.login.LoginValidationContext;

@Component
@Order(10)
public class LoginRequiredFieldsHandler extends AbstractLoginValidationHandler {

    private final LoginCredentialRules rules;

    public LoginRequiredFieldsHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(LoginValidationContext context) {
        rules.ensureRequestPresent(context.getDto());
        String normalizedUsername = rules.normalizeUsername(context.getDto().getUsername());
        rules.ensureUsernamePresent(normalizedUsername);
        context.setNormalizedUsername(normalizedUsername);
        handleNext(context);
    }
}
