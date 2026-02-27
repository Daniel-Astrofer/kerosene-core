package source.auth.application.service.authentication;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Service for verifying user credentials during signup.
 * Validates username and passphrase for format, length, and BIP39 compliance.
 *
 * Supports English (default bitcoinj wordlist) and Portuguese (BIP39 PT-BR).
 * A phrase is accepted if it is valid in EITHER language.
 */
@Service
public class SignupValidator implements SignupVerifier {

    private final UserRepository repository;

    /** Lazy-loaded Portuguese MnemonicCode instance. */
    private static final MnemonicCode PORTUGUESE_MNEMONIC;

    static {
        MnemonicCode pt = null;
        try (InputStream stream = new ClassPathResource("bip39_portuguese.txt").getInputStream()) {
            // null wordListDigest = skip checksum validation for the wordlist file itself
            pt = new MnemonicCode(stream, null);
        } catch (IOException e) {
            System.err.println("[BIP39] Failed to load Portuguese wordlist, PT mnemonics will not be accepted: "
                    + e.getMessage());
        } catch (Exception e) {
            System.err.println("[BIP39] Unexpected error loading Portuguese wordlist: " + e.getMessage());
        }
        PORTUGUESE_MNEMONIC = pt;
    }

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

    /**
     * Validates that the passphrase is a valid BIP39 mnemonic in English OR
     * Portuguese.
     * <p>
     * Strategy: try English first (zero overhead — uses the pre-built INSTANCE).
     * If that fails, try Portuguese. Only throw if BOTH fail.
     */
    @Override
    public void checkPassphraseBip39(String passphrase) {
        String normalizedPhrase = passphrase.trim().replaceAll("[\\s\\u00A0]+", " ");
        List<String> words = Arrays.asList(normalizedPhrase.split(" "));

        if (isValidEnglish(words) || isValidPortuguese(words)) {
            return; // accepted
        }

        // Neither language matched — figure out the most specific error
        deriveAndThrowError(words);
    }

    @Override
    public void checkUsernameExists(String username) {
        if (repository.findByUsername(username) != null) {
            throw new AuthExceptions.UserAlreadyExistsException(AuthConstants.ERR_USERNAME_ALREADY_EXISTS);
        }
    }

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

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isValidEnglish(List<String> words) {
        try {
            MnemonicCode.INSTANCE.check(words);
            return true;
        } catch (MnemonicException e) {
            return false;
        }
    }

    private boolean isValidPortuguese(List<String> words) {
        if (PORTUGUESE_MNEMONIC == null)
            return false;
        try {
            PORTUGUESE_MNEMONIC.check(words);
            return true;
        } catch (MnemonicException e) {
            return false;
        }
    }

    /**
     * Re-runs validation against English to extract the most meaningful error
     * code to return to the client.
     */
    private void deriveAndThrowError(List<String> words) {
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
}
