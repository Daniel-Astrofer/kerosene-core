package com.kerosene.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.login.LoginCredentialRules;
import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;

/** Second login step: increments the per-username anti-brute-force counter. */
@Component
@Order(20)
public class LoginRateLimitHandler extends AbstractLoginValidationHandler {

    /** Rules used to register the attempt counter. */
    private final LoginCredentialRules rules;

    /** Creates the throttle handler. */
    /** @param rules shared login rules */
    public LoginRateLimitHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    /** Stores the throttle key in the context and proceeds unless the attempt limit rejects the request. */
    /** @param context request context populated by the required-field handler */
    @Override
    public void handle(LoginValidationContext context) {
        context.setRateLimitKey(rules.registerRateLimitAttempt(context.getNormalizedUsername()));
        handleNext(context);
    }
}
