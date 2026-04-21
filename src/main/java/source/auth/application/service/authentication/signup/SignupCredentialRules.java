package source.auth.application.service.authentication.signup;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.port.out.AuthUserGateway;

@Service
public class SignupCredentialRules {

    private static final Logger log = LoggerFactory.getLogger(SignupCredentialRules.class);
    private static final MnemonicCode PORTUGUESE_MNEMONIC;

    static {
        MnemonicCode pt = null;
        try (InputStream stream = new ClassPathResource("bip39_portuguese.txt").getInputStream()) {
            pt = new MnemonicCode(stream, null);
        } catch (IOException e) {
            log.warn("[BIP39] Failed to load Portuguese wordlist, PT mnemonics will not be accepted: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.warn("[BIP39] Unexpected error loading Portuguese wordlist: {}", e.getMessage());
        }
        PORTUGUESE_MNEMONIC = pt;
    }

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
            throw new AuthExceptions.PassphraseCantBeNull(AuthConstants.ERR_PASSPHRASE_NULL);
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
        if (passphrase.length > AuthConstants.PASSPHRASE_MAX_LENGTH) {
            throw new AuthExceptions.CharacterLimitException(AuthConstants.ERR_PASSPHRASE_TOO_LONG);
        }
    }

    public void checkPassphraseBip39(char[] passphrase) {
        String normalizedPhrase = normalizePassphrase(passphrase);
        List<String> words = Arrays.asList(normalizedPhrase.split(" "));

        if (isValidEnglish(words) || isValidPortuguese(words)) {
            return;
        }

        deriveAndThrowError(words);
    }

    public void checkUsernameExists(String username) {
        if (userGateway.existsByUsername(username)) {
            throw new AuthExceptions.UserAlreadyExistsException(AuthConstants.ERR_USERNAME_ALREADY_EXISTS);
        }
    }

    private String normalizePassphrase(char[] input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean inSpace = false;
        for (char c : input) {
            if (Character.isWhitespace(c) || c == '\u00A0') {
                if (!inSpace) {
                    sb.append(' ');
                    inSpace = true;
                }
            } else {
                sb.append(c);
                inSpace = false;
            }
        }
        return sb.toString().trim();
    }

    private boolean isValidEnglish(List<String> words) {
        try {
            MnemonicCode.INSTANCE.check(words);
            return true;
        } catch (MnemonicException e) {
            return false;
        }
    }

    private boolean isValidPortuguese(List<String> words) {
        if (PORTUGUESE_MNEMONIC == null) {
            return false;
        }
        try {
            PORTUGUESE_MNEMONIC.check(words);
            return true;
        } catch (MnemonicException e) {
            return false;
        }
    }

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
