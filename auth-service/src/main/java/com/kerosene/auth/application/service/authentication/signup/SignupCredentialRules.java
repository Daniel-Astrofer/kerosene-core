package com.kerosene.auth.application.service.authentication.signup;

import org.springframework.stereotype.Service;

import com.kerosene.auth.AuthConstants;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.port.out.AuthUserGateway;

/** Implements persistent username uniqueness and signup credential validation rules. */
@Service
public class SignupCredentialRules {

    /** Account persistence port used to check username availability. */
    private final AuthUserGateway userGateway;

    /** Creates signup rules with the account lookup port. */
    /** @param userGateway outbound user persistence port */
    public SignupCredentialRules(AuthUserGateway userGateway) {
        this.userGateway = userGateway;
    }

    /** Rejects a null or blank username. */
    /** @param username candidate username */
    /** @throws AuthExceptions.UsernameCantBeNull when absent */
    public void checkUsernameNotNull(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthExceptions.UsernameCantBeNull(AuthConstants.ERR_USERNAME_NULL);
        }
    }

    /** Rejects a null or empty passphrase character array. */
    /** @param passphrase candidate passphrase characters */
    /** @throws AuthExceptions.PassphraseCantBeNull when absent */
    public void checkPassphraseNotNull(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new AuthExceptions.PassphraseCantBeNull(AuthConstants.ERR_PASSWORD_NULL);
        }
    }

    /** Requires the username to match the shared allowed-character pattern. */
    /** @param username candidate username */
    /** @throws AuthExceptions.InvalidCharacterUsername when a disallowed character occurs */
    public void checkUsernameFormat(String username) {
        if (!username.matches(AuthConstants.USERNAME_PATTERN)) {
            throw new AuthExceptions.InvalidCharacterUsername(AuthConstants.ERR_USERNAME_INVALID_CHARS);
        }
    }

    /** Enforces the configured maximum username length. */
    /** @param username candidate username */
    /** @throws AuthExceptions.CharacterLimitException when the username is too long */
    public void checkUsernameLength(String username) {
        if (username.length() > AuthConstants.USERNAME_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_USERNAME_TOO_LONG);
        }
    }

    /** Enforces the configured maximum passphrase length. */
    /** @param passphrase candidate passphrase characters */
    /** @throws AuthExceptions.CharacterLimitException when the passphrase is too long */
    public void checkPassphraseLength(char[] passphrase) {
        if (passphrase.length > AuthConstants.PASSWORD_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_PASSWORD_TOO_LONG);
        }
    }

    /** Enforces minimum length and uppercase, lowercase, digit, and non-whitespace symbol categories. */
    /** @param passphrase candidate passphrase characters */
    /** @throws AuthExceptions.InvalidPassphrase when length or composition requirements fail */
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

    /** Rejects a username already present in persistent storage. */
    /** @param username candidate username */
    /** @throws AuthExceptions.UserAlreadyExistsException when the username is taken */
    public void checkUsernameExists(String username) {
        if (userGateway.existsByUsername(username)) {
            throw new AuthExceptions.UserAlreadyExistsException(AuthConstants.ERR_USERNAME_ALREADY_EXISTS);
        }
    }
}
