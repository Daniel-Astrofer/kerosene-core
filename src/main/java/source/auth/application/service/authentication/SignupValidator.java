package source.auth.application.service.authentication;

import org.springframework.stereotype.Service;

import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.authentication.signup.SignupCredentialRules;
import source.auth.application.service.authentication.signup.SignupValidationContext;
import source.auth.application.service.authentication.signup.chain.SignupValidationChain;

/**
 * Service for verifying user credentials during signup.
 * Validates username and passphrase for format, length, and BIP39 compliance.
 *
 * Supports English (default bitcoinj wordlist) and Portuguese (BIP39 PT-BR).
 * A phrase is accepted if it is valid in EITHER language.
 */
@Service
public class SignupValidator implements SignupVerifier {

    private final SignupCredentialRules rules;
    private final SignupValidationChain validationChain;

    public SignupValidator(SignupCredentialRules rules, SignupValidationChain validationChain) {
        this.rules = rules;
        this.validationChain = validationChain;
    }

    @Override
    public void checkUsernameNotNull(String username) {
        rules.checkUsernameNotNull(username);
    }

    @Override
    public void checkPassphraseNotNull(char[] passphrase) {
        rules.checkPassphraseNotNull(passphrase);
    }

    @Override
    public void checkUsernameFormat(String username) {
        rules.checkUsernameFormat(username);
    }

    @Override
    public void checkUsernameLength(String username) {
        rules.checkUsernameLength(username);
    }

    @Override
    public void checkPassphraseLength(char[] passphrase) {
        rules.checkPassphraseLength(passphrase);
    }

    @Override
    public void checkPassphraseBip39(char[] passphrase) {
        rules.checkPassphraseBip39(passphrase);
    }

    @Override
    public void checkUsernameExists(String username) {
        rules.checkUsernameExists(username);
    }

    @Override
    public boolean verify(String username, char[] passphrase) {
        validationChain.validate(new SignupValidationContext(username, passphrase));
        return true;
    }
}
