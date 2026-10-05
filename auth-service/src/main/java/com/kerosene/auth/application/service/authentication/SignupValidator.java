package com.kerosene.auth.application.service.authentication;

import org.springframework.stereotype.Service;

import com.kerosene.auth.application.service.authentication.contracts.SignupVerifier;
import com.kerosene.auth.application.service.authentication.signup.SignupCredentialRules;
import com.kerosene.auth.application.service.authentication.signup.SignupValidationContext;
import com.kerosene.auth.application.service.authentication.signup.chain.SignupValidationChain;

/**
 * Service for verifying user credentials during signup.
 * Validates username and passphrase for format, length, and BIP39 compliance.
 *
 * Supports English (default bitcoinj wordlist) and Portuguese (BIP39 PT-BR).
 * A phrase is accepted if it is valid in EITHER language.
 */
/** Adapts signup credential checks to the configured ordered validation chain. */
@Service
public class SignupValidator implements SignupVerifier {

    /** Reusable field, format, length, and uniqueness checks. */
    private final SignupCredentialRules rules;
    /** Ordered signup validation sequence. */
    private final SignupValidationChain validationChain;

    /** Creates the signup validator. */
    /** @param rules signup credential rules */
    /** @param validationChain ordered signup handler chain */
    public SignupValidator(SignupCredentialRules rules, SignupValidationChain validationChain) {
        this.rules = rules;
        this.validationChain = validationChain;
    }

    /** Checks that a username is supplied. */
    /** @param username candidate username */
    @Override
    public void checkUsernameNotNull(String username) {
        rules.checkUsernameNotNull(username);
    }

    /** Checks that the mutable passphrase input is supplied. */
    /** @param passphrase candidate passphrase characters */
    @Override
    public void checkPassphraseNotNull(char[] passphrase) {
        rules.checkPassphraseNotNull(passphrase);
    }

    /** Validates username characters using the shared username pattern. */
    /** @param username candidate username */
    @Override
    public void checkUsernameFormat(String username) {
        rules.checkUsernameFormat(username);
    }

    /** Validates username maximum length. */
    /** @param username candidate username */
    @Override
    public void checkUsernameLength(String username) {
        rules.checkUsernameLength(username);
    }

    /** Validates passphrase maximum length. */
    /** @param passphrase candidate passphrase characters */
    @Override
    public void checkPassphraseLength(char[] passphrase) {
        rules.checkPassphraseLength(passphrase);
    }

    /** Validates passphrase minimum length and required character categories. */
    /** @param passphrase candidate passphrase characters */
    @Override
    public void checkPassphraseBip39(char[] passphrase) {
        rules.checkPassphraseBip39(passphrase);
    }

    /** Rejects usernames already present in persistent storage. */
    /** @param username candidate username */
    @Override
    public void checkUsernameExists(String username) {
        rules.checkUsernameExists(username);
    }

    /** Runs all signup checks in the ordered chain and returns true after successful completion. */
    /** @param username candidate username */
    /** @param passphrase candidate passphrase characters */
    /** @return true when every handler completes without throwing */
    @Override
    public boolean verify(String username, char[] passphrase) {
        validationChain.validate(new SignupValidationContext(username, passphrase));
        return true;
    }
}
