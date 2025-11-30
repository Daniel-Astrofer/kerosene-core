package source.auth.application.service.authentication;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service for verifying user credentials.
 * Checks username and passphrase for validity, format, length, and existence.
 */
@Service
public class SignupValidator implements SignupVerifier {

    private static final int USERNAME_CHARACTER_LIMIT = 50;
    private static final int PASSPHRASE_CHARACTER_MAX_LIMIT = 161;

    private final UserRepository repository;


    public SignupValidator(UserRepository repository) {
        this.repository = repository;
    }


    @Override
    public void checkUsernameNotNull(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthExceptions.UsernameCantBeNull("Username cannot be null");
        }
    }

    @Override
    public void checkPassphraseNotNull(String passphrase) {
        if (passphrase == null) {
            throw new AuthExceptions.PassphraseCantBeNull("Passphrase cannot be null");
        }
    }

    @Override
    public void checkUsernameFormat(String username) {
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new AuthExceptions.InvalidCharacterUsername("Invalid character in username");
        }
    }

    @Override
    public void checkUsernameLength(String username) {
        if (username.length() > USERNAME_CHARACTER_LIMIT) {
            throw new AuthExceptions.UsernameCharacterLimitException("Username character limit exceeded");
        }
    }

    @Override
    public void checkPassphraseLength(String passphrase) {
        if (passphrase.length() > PASSPHRASE_CHARACTER_MAX_LIMIT) {
            throw new AuthExceptions.UsernameCharacterLimitException("Passphrase character limit exceeded");
        }
    }

    @Override
    public void checkPassphraseBip39(String passphrase) {
        String phrase = passphrase.trim().replaceAll("[\\s\\u00A0]+", " ");
        List<String> words = Arrays.asList(phrase.split(" "));

        try {
            MnemonicCode.INSTANCE.check(words);
        } catch (MnemonicException.MnemonicWordException e) {
            throw new AuthExceptions.InvalidPassphrase("Unrecognized word in passphrase");
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new AuthExceptions.InvalidPassphrase("Passphrase length incompatible with BIP39");
        } catch (MnemonicException e) {
            throw new AuthExceptions.InvalidPassphrase("Invalid passphrase");
        }
    }

    @Override
    public void checkUsernameExists(String username) {
        if (repository.findByUsername(username).isPresent()) {
            throw new AuthExceptions.UserAlreadyExistsException("User already exists");
        }
    }

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
