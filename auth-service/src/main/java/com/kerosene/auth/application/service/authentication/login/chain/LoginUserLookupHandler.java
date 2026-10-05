package com.kerosene.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.login.LoginCredentialRules;
import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;

/** Third login step: resolves the account associated with the normalized username. */
@Component
@Order(30)
public class LoginUserLookupHandler extends AbstractLoginValidationHandler {

    /** Rules used to retrieve the account or return generic invalid credentials. */
    private final LoginCredentialRules rules;

    /** Creates the account lookup handler. */
    /** @param rules shared login rules */
    public LoginUserLookupHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    /** Loads the user into context before continuing to passphrase verification. */
    /** @param context request context containing the normalized username */
    @Override
    public void handle(LoginValidationContext context) {
        context.setUser(rules.loadUser(context.getNormalizedUsername()));
        handleNext(context);
    }
}
