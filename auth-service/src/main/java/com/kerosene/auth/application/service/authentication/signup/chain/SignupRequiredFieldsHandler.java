package com.kerosene.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.signup.SignupCredentialRules;
import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;

/** First signup step: requires a username and passphrase before later validation. */
@Component
@Order(10)
public class SignupRequiredFieldsHandler extends AbstractSignupValidationHandler {

    /** Shared signup input rules. */
    private final SignupCredentialRules rules;

    /** Creates the required-field handler. */
    /** @param rules signup validation rules */
    public SignupRequiredFieldsHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    /** Validates username/passphrase presence and continues the chain. */
    /** @param context signup values under validation */
    @Override
    public void handle(SignupValidationContext context) {
        rules.checkUsernameNotNull(context.username());
        rules.checkPassphraseNotNull(context.passphrase());
        handleNext(context);
    }
}
