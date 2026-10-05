package com.kerosene.auth.application.service.authentication.login.chain;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.authentication.login.LoginCredentialRules;
import com.kerosene.auth.application.service.authentication.login.LoginValidationContext;

/** Final login step: normalizes the passphrase, verifies its hash, and continues on success. */
@Component
@Order(40)
public class LoginPassphraseVerificationHandler extends AbstractLoginValidationHandler {

    /** Rules for normalization and hash verification. */
    private final LoginCredentialRules rules;

    /** Creates the passphrase verification handler. */
    /** @param rules shared login rules */
    public LoginPassphraseVerificationHandler(LoginCredentialRules rules) {
        this.rules = rules;
    }

    /** Stores a normalized secret buffer, verifies it against the loaded user, then continues. */
    /** @param context context containing request and resolved user */
    @Override
    public void handle(LoginValidationContext context) {
        char[] normalizedPassphrase = rules.normalizePassphrase(context.getDto().getPassphrase());
        context.setNormalizedPassphrase(normalizedPassphrase);
        rules.verifyPassphrase(normalizedPassphrase, context.getUser());
        handleNext(context);
    }
}
