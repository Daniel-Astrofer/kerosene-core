package source.bitcoinaccounts.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitcoinAccountSecurityServiceTest {

    private final BitcoinAccountSecurityService service = new BitcoinAccountSecurityService();

    @Test
    void rejectsExtendedPrivateKeys() {
        String xprv = "xprv" + "A".repeat(80);

        assertThrows(IllegalArgumentException.class,
                () -> service.validatePublicWatchOnlyMaterial(null, xprv));
    }

    @Test
    void rejectsMnemonicLikeMaterial() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

        assertThrows(IllegalArgumentException.class,
                () -> service.validatePublicWatchOnlyMaterial(mnemonic, null));
    }

    @Test
    void acceptsPublicWatchOnlyMaterial() {
        String descriptor = "wpkh([d34db33f/84'/0'/0']xpub661MyMwAqRbcFfake/0/*)";

        assertDoesNotThrow(() -> service.validatePublicWatchOnlyMaterial(descriptor, null));
    }

    @Test
    void rejectsInvalidXpub() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validatePublicWatchOnlyMaterial(null, "not-an-xpub"));
    }

    @Test
    void validatesColdWalletMetadata() {
        assertDoesNotThrow(() -> service.validateColdWalletMetadata("d34db33f", "m/84'/0'/0'"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateColdWalletMetadata("nothex", "m/84'/0'/0'"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateColdWalletMetadata("d34db33f", "84/0/0"));
    }
}
