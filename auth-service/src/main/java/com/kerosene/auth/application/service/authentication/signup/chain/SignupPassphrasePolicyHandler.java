package com.kerosene.auth.application.service.authentication.signup.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.signup.SignupCredentialRules;
import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;

/** Third signup step: enforces passphrase length and composition policy. */
@Component
@Order(30)
public class SignupPassphrasePolicyHandler extends AbstractSignupValidationHandler {

    /** Shared signup input rules. */
    private final SignupCredentialRules rules;

    /** Creates the passphrase policy handler. */
    /** @param rules signup validation rules */
    public SignupPassphrasePolicyHandler(SignupCredentialRules rules) {
        this.rules = rules;
    }

    /** Checks passphrase maximum length and character categories before continuing. */
    /** @param context signup values under validation */
    @Override
    public void handle(SignupValidationContext context) {
        rules.checkPassphraseLength(context.passphrase());
        rules.checkPassphraseBip39(context.passphrase());
        handleNext(context);
    }
}
