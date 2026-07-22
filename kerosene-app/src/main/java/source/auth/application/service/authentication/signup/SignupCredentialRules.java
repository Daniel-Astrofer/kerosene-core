package source.auth.application.service.authentication.signup;

import org.springframework.stereotype.Service;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthUserGateway;

@Service
public class SignupCredentialRules {

    private final AuthUserGateway userGateway;

    public SignupCredentialRules(AuthUserGateway userGateway) {
        this.userGateway = userGateway;
    }

    public void checkUsernameNotNull(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthExceptions.UsernameCantBeNull(AuthConstants.ERR_USERNAME_NULL);
        }
    }

    public void checkPassphraseNotNull(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new AuthExceptions.PassphraseCantBeNull(AuthConstants.ERR_PASSWORD_NULL);
        }
    }

    public void checkUsernameFormat(String username) {
        if (!username.matches(AuthConstants.USERNAME_PATTERN)) {
            throw new AuthExceptions.InvalidCharacterUsername(AuthConstants.ERR_USERNAME_INVALID_CHARS);
        }
    }

    public void checkUsernameLength(String username) {
        if (username.length() > AuthConstants.USERNAME_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_USERNAME_TOO_LONG);
        }
    }

    public void checkPassphraseLength(char[] passphrase) {
        if (passphrase.length > AuthConstants.PASSWORD_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_PASSWORD_TOO_LONG);
        }
    }

    public void checkPassphraseBip39(char[] passphrase) {
        if (passphrase.length < AuthConstants.PASSWORD_MIN_LENGTH) {
            throw new AuthExceptions.InvalidPassphrase(AuthConstants.ERR_PASSWORD_TOO_SHORT);
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (char value : passphrase) {
            if (Character.isUpperCase(value)) {
                hasUpper = true;
            } else if (Character.isLowerCase(value)) {
                hasLower = true;
            } else if (Character.isDigit(value)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(value)) {
                hasSymbol = true;
            }
        }

        if (!(hasUpper && hasLower && hasDigit && hasSymbol)) {
            throw new AuthExceptions.InvalidPassphrase(AuthConstants.ERR_PASSWORD_WEAK);
        }
    }

    public void checkUsernameExists(String username) {
        if (userGateway.existsByUsername(username)) {
            throw new AuthExceptions.UserAlreadyExistsException(AuthConstants.ERR_USERNAME_ALREADY_EXISTS);
        }
    }
}
