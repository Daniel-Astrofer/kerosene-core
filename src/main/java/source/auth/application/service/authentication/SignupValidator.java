package source.auth.application.service.authentication;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service for verifying user credentials during signup.
 * Validates username and passphrase for format, length, and BIP39 compliance.
 */
@Service
public class SignupValidator implements SignupVerifier {

    private final UserRepository repository;

    public SignupValidator(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public void checkUsernameNotNull(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthExceptions.UsernameCantBeNull(AuthConstants.ERR_USERNAME_NULL);
        }
    }

    @Override
    public void checkPassphraseNotNull(String passphrase) {
        if (passphrase == null) {
            throw new AuthExceptions.PassphraseCantBeNull(AuthConstants.ERR_PASSPHRASE_NULL);
        }
    }

    @Override
    public void checkUsernameFormat(String username) {
        if (!username.matches(AuthConstants.USERNAME_PATTERN)) {
            throw new AuthExceptions.InvalidCharacterUsername(AuthConstants.ERR_USERNAME_INVALID_CHARS);
        }
    }

    @Override
    public void checkUsernameLength(String username) {
        if (username.length() > AuthConstants.USERNAME_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_USERNAME_TOO_LONG);
        }
    }

    @Override
    public void checkPassphraseLength(String passphrase) {
        if (passphrase.length() > AuthConstants.PASSPHRASE_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_PASSPHRASE_TOO_LONG);
        }
    }

    @Override
    public void checkPassphraseBip39(String passphrase) {
        String normalizedPhrase = passphrase.trim().replaceAll("[\\s\\u00A0]+", " ");
        List<String> words = Arrays.asList(normalizedPhrase.split(" "));

        try {
            MnemonicCode.INSTANCE.check(words);
        } catch (MnemonicException.MnemonicWordException e) {
            throw new AuthExceptions.InvalidPassphrase(AuthConstants.ERR_PASSPHRASE_INVALID_WORD);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new AuthExceptions.InvalidPassphrase(AuthConstants.ERR_PASSPHRASE_INVALID_LENGTH);
        } catch (MnemonicException e) {
            throw new AuthExceptions.InvalidPassphrase(AuthConstants.ERR_PASSPHRASE_INVALID);
        }
    }

    @Override
    public void checkUsernameExists(String username) {
        if (repository.findByUsername(username) != null ) {
            throw new AuthExceptions.UserAlreadyExistsException(AuthConstants.ERR_USERNAME_ALREADY_EXISTS);
        }
    }

    /**
     * Performs all validation checks for signup.
     * 
     * @param username the username to validate
     * @param passphrase the passphrase to validate
     * @return true if all validations pass
     * @throws AuthExceptions.AuthValidationException if any validation fails
     */
    @Override
    public boolean verify(String username, String passphrase) {
        checkUsernameNotNull(username);
        checkPassphraseNotNull(passphrase);
        checkUsernameFormat(username);
        checkUsernameLength(username);
        checkPassphraseLength(passphrase);
        checkPassphraseBip39(passphrase);
        checkUsernameExists(username);

        return true;
    }
}
