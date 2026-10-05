package com.kerosene.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.signup.SignupCredentialRules;
import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;

/** Final signup step: rejects usernames already stored for another account. */
@Component
@Order(40)
public class SignupUsernameAvailabilityHandler extends AbstractSignupValidationHandler {

    /** Shared signup uniqueness rule. */
    private final SignupCredentialRules rules;

    /** Creates the username availability handler. */
    /** @param rules signup validation rules */
    public SignupUsernameAvailabilityHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    /** Checks username uniqueness and ends the chain on success. */
    /** @param context signup values under validation */
    @Override
    public void handle(SignupValidationContext context) {
        rules.checkUsernameExists(context.username());
        handleNext(context);
    }
}
