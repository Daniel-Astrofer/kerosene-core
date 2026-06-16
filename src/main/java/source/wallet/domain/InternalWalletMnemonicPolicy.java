package source.wallet.domain;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import source.auth.AuthConstants;
import source.auth.AuthExceptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class InternalWalletMnemonicPolicy {

    private static final Logger log = LoggerFactory.getLogger(InternalWalletMnemonicPolicy.class);
    private static final MnemonicCode PORTUGUESE_MNEMONIC;

    static {
        MnemonicCode pt = null;
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("bip39_portuguese.txt")) {
            if (stream != null) {
                pt = new MnemonicCode(stream, null);
            }
        } catch (IOException e) {
            log.warn("[BIP39] Failed to load Portuguese wordlist, PT mnemonics will not be accepted: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.warn("[BIP39] Unexpected error loading Portuguese wordlist: {}", e.getMessage());
        }
        PORTUGUESE_MNEMONIC = pt;
    }

    public void validate(char[] mnemonic) {
        if (mnemonic == null || mnemonic.length == 0) {
            throw new AuthExceptions.InvalidPassphrase(AuthConstants.ERR_PASSWORD_NULL);
        }

        String normalizedPhrase = normalize(mnemonic);
        List<String> words = Arrays.asList(normalizedPhrase.split(" "));

        try {
            if (isValidEnglish(words) || isValidPortuguese(words)) {
                return;
            }
            deriveAndThrowError(words);
        } finally {
            Arrays.fill(mnemonic, '\0');
        }
    }

    private String normalize(char[] input) {
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
            throw new AuthExceptions.InvalidPassphrase("Internal wallet seed contains unrecognized BIP39 word.");
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new AuthExceptions.InvalidPassphrase("Internal wallet seed length is incompatible with BIP39.");
        } catch (MnemonicException e) {
            throw new AuthExceptions.InvalidPassphrase("Invalid internal wallet seed format.");
        }
    }
}
