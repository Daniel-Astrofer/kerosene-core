package com.kerosene.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.login.LoginCredentialRules;
import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;

/** First login step: requires the DTO and normalizes its username into the shared context. */
@Component
@Order(10)
public class LoginRequiredFieldsHandler extends AbstractLoginValidationHandler {

    /** Rules used to validate the request and username. */
    private final LoginCredentialRules rules;

    /** Creates the required-field handler. */
    /** @param rules shared login rules */
    public LoginRequiredFieldsHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    /** Validates DTO and username presence, stores the normalized username, then continues. */
    /** @param context request context updated for later handlers */
    @Override
    public void handle(LoginValidationContext context) {
        rules.ensureRequestPresent(context.getDto());
        String normalizedUsername = rules.normalizeUsername(context.getDto().getUsername());
        rules.ensureUsernamePresent(normalizedUsername);
        context.setNormalizedUsername(normalizedUsername);
        handleNext(context);
    }
}
