package source.wallet.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import source.auth.AuthExceptions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("InternalWalletMnemonicPolicy Tests")
class InternalWalletMnemonicPolicyTest {

    private final InternalWalletMnemonicPolicy policy = new InternalWalletMnemonicPolicy();

    @Test
    @DisplayName("Validates correct English mnemonic phrase")
    void testValidEnglishMnemonic() {
        String validMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        assertDoesNotThrow(() -> policy.validate(validMnemonic.toCharArray()));
    }

    @Test
    @DisplayName("Rejects null or empty mnemonic")
    void testEmptyMnemonic() {
        assertThrows(AuthExceptions.InvalidPassphrase.class, () -> policy.validate(null));
        assertThrows(AuthExceptions.InvalidPassphrase.class, () -> policy.validate(new char[0]));
    }

    @Test
    @DisplayName("Rejects mnemonic with unrecognized words")
    void testUnrecognizedWords() {
        String invalidMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon kerosenedoesnotexist";
        AuthExceptions.InvalidPassphrase ex = assertThrows(
                AuthExceptions.InvalidPassphrase.class, 
                () -> policy.validate(invalidMnemonic.toCharArray())
        );
        assertEquals("Internal wallet seed contains unrecognized BIP39 word.", ex.getMessage());
    }

    @Test
    @DisplayName("Rejects mnemonic with invalid length")
    void testInvalidLength() {
        String invalidLengthMnemonic = "abandon abandon abandon abandon";
        AuthExceptions.InvalidPassphrase ex = assertThrows(
                AuthExceptions.InvalidPassphrase.class, 
                () -> policy.validate(invalidLengthMnemonic.toCharArray())
        );
        assertEquals("Internal wallet seed length is incompatible with BIP39.", ex.getMessage());
    }
    
    @Test
    @DisplayName("Rejects mnemonic with invalid checksum")
    void testInvalidChecksum() {
        // 'abandon' 12 times has an invalid checksum (should end with 'about')
        String invalidMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon";
        AuthExceptions.InvalidPassphrase ex = assertThrows(
                AuthExceptions.InvalidPassphrase.class, 
                () -> policy.validate(invalidMnemonic.toCharArray())
        );
        assertEquals("Invalid internal wallet seed format.", ex.getMessage());
    }
}
