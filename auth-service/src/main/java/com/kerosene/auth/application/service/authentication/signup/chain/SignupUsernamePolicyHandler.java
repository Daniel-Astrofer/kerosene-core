package com.kerosene.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.signup.SignupCredentialRules;
import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;

/** Second signup step: enforces allowed username characters and length. */
@Component
@Order(20)
public class SignupUsernamePolicyHandler extends AbstractSignupValidationHandler {

    /** Shared signup input rules. */
    private final SignupCredentialRules rules;

    /** Creates the username policy handler. */
    /** @param rules signup validation rules */
    public SignupUsernamePolicyHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    /** Checks username format and maximum length before continuing. */
    /** @param context signup values under validation */
    @Override
    public void handle(SignupValidationContext context) {
        rules.checkUsernameFormat(context.username());
        rules.checkUsernameLength(context.username());
        handleNext(context);
    }
}
